package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.local.AppDatabase
import com.example.data.model.CachedLyricsEntity
import com.example.data.repository.LyricsRepository
import com.example.util.LrcLine
import com.example.util.LrcParser
import com.example.util.PowerampPlaybackPositionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingLyricsOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "FloatingLyricsService"
        private const val CHANNEL_ID = "floating_lyrics_channel"
        private const val NOTIFICATION_ID = 2002

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingLyricsOverlayService::class.java)
            context.stopService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = store

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    private lateinit var playbackPositionSource: PowerampPlaybackPositionSource
    private lateinit var lyricsRepository: LyricsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI state states
    private var _currentLyricsLine = mutableStateOf("")
    private var _trackTitleState = mutableStateOf("")
    private var _trackArtistState = mutableStateOf("")
    private var _isPlayingState = mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize repository
        val db = AppDatabase.getDatabase(applicationContext)
        lyricsRepository = LyricsRepository(db.lyricsDao())

        // Initialize playback source
        playbackPositionSource = PowerampPlaybackPositionSource(application, serviceScope)

        // Observe playback and fetch lyrics
        observePlaybackAndLyrics()

        // Setup notification channel and start foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Create overlay compose view
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Lyrics Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating lyrics overlay service running"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Lyrics Active")
            .setContentText("Lyrics are floating above other apps.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun observePlaybackAndLyrics() {
        var currentLrcLines: List<LrcLine> = emptyList()

        // 1. Observe track metadata to load cached lyrics
        serviceScope.launch {
            combine(
                playbackPositionSource.currentTrackTitle,
                playbackPositionSource.currentTrackArtist,
                playbackPositionSource.currentTrackAlbum
            ) { title, artist, album ->
                Triple(title, artist, album)
            }.collectLatest { (title, artist, album) ->
                _trackTitleState.value = title
                _trackArtistState.value = artist
                _currentLyricsLine.value = if (title.isEmpty()) "Waiting for music..." else "Loading lyrics..."

                if (title.isNotEmpty()) {
                    val key = CachedLyricsEntity.generateKey(title, artist, album, 240000) // fallback duration
                    // Let's first search in database
                    val cached = lyricsRepository.getCachedLyrics(key)
                    if (cached != null) {
                        currentLrcLines = LrcParser.parse(cached.syncedLyrics)
                        if (currentLrcLines.isEmpty()) {
                            _currentLyricsLine.value = cached.plainLyrics?.takeIf { it.isNotEmpty() }?.lines()?.firstOrNull() ?: "No synced lyrics"
                        }
                    } else {
                        // Let's search by artist & title to see if we have ANY match in DB
                        currentLrcLines = emptyList()
                        _currentLyricsLine.value = "No lyrics cached"
                    }
                } else {
                    currentLrcLines = emptyList()
                }
            }
        }

        // 2. Observe position and isPlaying to sync current lyric line
        serviceScope.launch {
            playbackPositionSource.isPlaying.collectLatest { isPlaying ->
                _isPlayingState.value = isPlaying
            }
        }

        serviceScope.launch {
            playbackPositionSource.currentPositionMs.collectLatest { positionMs ->
                if (currentLrcLines.isNotEmpty()) {
                    val activeLine = currentLrcLines.lastOrNull { it.timestampMs <= positionMs }
                    _currentLyricsLine.value = activeLine?.text ?: _trackTitleState.value
                }
            }
        }
    }

    private fun createOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 150 // initial vertical offset
        }

        val context = this
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(context)
            setViewTreeSavedStateRegistryOwner(context)
            setViewTreeViewModelStoreOwner(context)

            setContent {
                MaterialTheme {
                    FloatingLyricsOverlayContent(
                        lyricsLine = _currentLyricsLine.value,
                        trackTitle = _trackTitleState.value,
                        trackArtist = _trackArtistState.value,
                        onDrag = { dx, dy ->
                            params?.let { p ->
                                p.x += dx.roundToInt()
                                p.y += dy.roundToInt()
                                windowManager.updateViewLayout(this@apply, p)
                            }
                        },
                        onClose = {
                            stopSelf()
                        }
                    )
                }
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        playbackPositionSource.unregister()
        serviceScope.cancel()
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view on destroy", e)
            }
        }
        super.onDestroy()
    }
}

@Composable
fun FloatingLyricsOverlayContent(
    lyricsLine: String,
    trackTitle: String,
    trackArtist: String,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Indicator Handle / Icon
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Song Info",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                if (expanded && trackTitle.isNotEmpty()) {
                    Text(
                        text = trackTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = trackArtist,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = lyricsLine.takeIf { it.isNotEmpty() } ?: "No Active Lyrics",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Overlay",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
