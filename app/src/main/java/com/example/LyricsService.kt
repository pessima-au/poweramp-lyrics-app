package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.local.AppDatabase
import com.example.data.repository.LyricsRepository
import com.example.data.repository.TrackRepository
import kotlinx.coroutines.*

class LyricsService : Service() {

    companion object {
        private const val TAG = "LyricsService"
        private const val CHANNEL_ID = "lyrics_service_channel"
        private const val NOTIFICATION_ID = 2001

        private const val ACTION_LYRICS_REQUEST = "com.example.action.LYRICS_REQUEST"
        private const val ACTION_BATCH_DOWNLOAD = "com.example.action.BATCH_DOWNLOAD"
        private const val ACTION_UPDATE_STATUS = "com.example.action.UPDATE_STATUS"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_TRACK_IDS = "extra_track_ids"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"

        fun startForLyricsRequest(context: Context, title: String, artist: String) {
            val intent = Intent(context, LyricsService::class.java).apply {
                action = ACTION_LYRICS_REQUEST
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            startServiceSafely(context, intent)
        }

        fun startForBatchDownload(context: Context, trackIds: LongArray) {
            val intent = Intent(context, LyricsService::class.java).apply {
                action = ACTION_BATCH_DOWNLOAD
                putExtra(EXTRA_TRACK_IDS, trackIds)
            }
            startServiceSafely(context, intent)
        }

        fun updateStatus(context: Context, statusText: String) {
            val intent = Intent(context, LyricsService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            startServiceSafely(context, intent)
        }

        private fun startServiceSafely(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service (Android 12+ restrictions background start block)", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_LYRICS_REQUEST -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown Track"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"
                showForegroundNotification("Searching lyrics for: $title", "Artist: $artist")
            }
            ACTION_BATCH_DOWNLOAD -> {
                val trackIds = intent.getLongArrayExtra(EXTRA_TRACK_IDS)
                if (trackIds != null && trackIds.isNotEmpty()) {
                    startBatchDownload(trackIds)
                } else {
                    stopSelf()
                }
            }
            ACTION_UPDATE_STATUS -> {
                val text = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: ""
                if (text.isNotEmpty()) {
                    showForegroundNotification("Verse Lyrics Plugin", text)
                    // Auto-dismiss service after 10 seconds if no active batch download is running
                    if (batchJob == null || !batchJob!!.isActive) {
                        serviceScope.launch {
                            delay(10000L)
                            if (batchJob == null || !batchJob!!.isActive) {
                                stopSelf()
                            }
                        }
                    }
                }
            }
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showForegroundNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search) // Standard system fallback icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground due to launch restrictions", e)
        }
    }

    private fun startBatchDownload(trackIds: LongArray) {
        batchJob?.cancel()
        batchJob = serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val lyricsDao = db.lyricsDao()
                val trackRepository = TrackRepository(applicationContext, lyricsDao)
                val lyricsRepository = LyricsRepository(lyricsDao)

                val allTracks = trackRepository.getTracks(useMockFallback = true)
                val tracksToDownload = allTracks.filter { trackIds.contains(it.id) }
                val total = tracksToDownload.size

                for ((index, track) in tracksToDownload.withIndex()) {
                    showForegroundNotification(
                        "Downloading bulk lyrics (${index + 1}/$total)",
                        "Fetching: ${track.title}"
                    )

                    try {
                        lyricsRepository.getLyricsForTrack(
                            track.title, track.artist, track.album, track.durationMs, forceRefresh = true
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch download failed for ${track.title}", e)
                    }
                }

                showForegroundNotification("Verse Lyrics Plugin", "Completed downloading $total tracks!")
                delay(3000L)
            } catch (e: CancellationException) {
                Log.d(TAG, "Batch download cancelled")
            } finally {
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lyrics Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows lyrics search and download status."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
