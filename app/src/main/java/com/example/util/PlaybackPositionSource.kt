package com.example.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
import android.util.Log

interface PlaybackPositionSource {
    val currentPositionMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    fun setPlaying(playing: Boolean)
    fun seekTo(positionMs: Long)
    fun setDuration(durationMs: Long)
}

class PowerampPlaybackPositionSource(
    private val context: android.content.Context,
    private val scope: CoroutineScope
) : android.content.BroadcastReceiver(), PlaybackPositionSource {

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow("")
    val currentTrackTitle: StateFlow<String> = _currentTrackTitle.asStateFlow()

    private val _currentTrackArtist = MutableStateFlow("")
    val currentTrackArtist: StateFlow<String> = _currentTrackArtist.asStateFlow()

    private val _currentTrackAlbum = MutableStateFlow("")
    val currentTrackAlbum: StateFlow<String> = _currentTrackAlbum.asStateFlow()

    private val _currentTrackPath = MutableStateFlow("")
    val currentTrackPath: StateFlow<String> = _currentTrackPath.asStateFlow()

    private val _rawPosSyncFlow = MutableStateFlow<Long?>(null)
    
    private val _throttledPosSync = MutableStateFlow(0L)
    val throttledPosSync: StateFlow<Long> = _throttledPosSync.asStateFlow()

    private var durationMs = 0L
    private var startTimeMs = 0L
    private var startPositionMs = 0L
    private var isRegistered = false
    private var tickerJob: Job? = null

    companion object {
        private const val TAG = "PowerampPlaybackSource"
    }

    init {
        scope.launch(Dispatchers.Default) {
            _rawPosSyncFlow
                .filterNotNull()
                .sample(1000L)
                .collect { posMs ->
                    _throttledPosSync.value = posMs
                    updatePosition(posMs)
                    Log.d(TAG, "Processed throttled position sync: $posMs ms")
                }
        }
        register()
        requestPosSync()
    }

    fun register() {
        if (isRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction(PowerampAPI.ACTION_TRACK_POS_SYNC)
            addAction(PowerampAPI.ACTION_TRACK_CHANGED)
            addAction(PowerampAPI.ACTION_TRACK_CHANGED_EXPLICIT)
            addAction(PowerampAPI.ACTION_STATUS_CHANGED)
            addAction(PowerampAPI.ACTION_STATUS_CHANGED_EXPLICIT)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, this, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        isRegistered = true
        startTicker()
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(this)
        } catch (ignored: Exception) { }
        isRegistered = false
        stopTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(100)
                if (_isPlaying.value && startTimeMs > 0) {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    val nextPos = startPositionMs + elapsed
                    _currentPositionMs.value = if (durationMs > 0) nextPos.coerceAtMost(durationMs) else nextPos
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun requestPosSync() {
        try {
            PowerampAPIHelper.sendPAIntent(context, android.content.Intent(PowerampAPI.ACTION_API_COMMAND)
                .putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.POS_SYNC))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send POS_SYNC command to Poweramp", e)
        }
    }

    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        when (intent.action) {
            PowerampAPI.ACTION_TRACK_POS_SYNC -> {
                val posObj = intent.extras?.get(PowerampAPI.Track.POSITION)
                if (posObj == null) {
                    Log.w(TAG, "ACTION_TRACK_POS_SYNC: Poweramp returned null track position")
                } else {
                    try {
                        val posSec = when (posObj) {
                            is Number -> posObj.toInt()
                            is String -> posObj.toIntOrNull() ?: 0
                            else -> 0
                        }
                        _rawPosSyncFlow.value = posSec * 1000L
                        Log.d(TAG, "ACTION_TRACK_POS_SYNC position=$posSec sec (queued for throttling)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing position extra in ACTION_TRACK_POS_SYNC: ${e.message}", e)
                    }
                }
            }
            PowerampAPI.ACTION_TRACK_CHANGED,
            PowerampAPI.ACTION_TRACK_CHANGED_EXPLICIT -> {
                val trackBundle = intent.getBundleExtra(PowerampAPI.EXTRA_TRACK)
                if (trackBundle != null) {
                    val title = trackBundle.getString(PowerampAPI.Track.TITLE) ?: ""
                    val artist = trackBundle.getString(PowerampAPI.Track.ARTIST) ?: ""
                    val album = trackBundle.getString(PowerampAPI.Track.ALBUM) ?: ""
                    val path = trackBundle.getString(PowerampAPI.Track.PATH) ?: ""
                    var dur = trackBundle.getInt(PowerampAPI.Track.DURATION_MS, 0).toLong()
                    if (dur <= 0) {
                        dur = trackBundle.getInt(PowerampAPI.Track.DURATION, 0) * 1000L
                    }

                    _currentTrackTitle.value = title
                    _currentTrackArtist.value = artist
                    _currentTrackAlbum.value = album
                    _currentTrackPath.value = path
                    setDuration(dur)

                    Log.d(TAG, "TRACK_CHANGED: action=${intent.action} title=$title, artist=$artist, path=$path, dur=$dur ms")
                    requestPosSync()
                }
            }
            PowerampAPI.ACTION_STATUS_CHANGED,
            PowerampAPI.ACTION_STATUS_CHANGED_EXPLICIT -> {
                val paused = intent.getBooleanExtra(PowerampAPI.EXTRA_PAUSED, true)
                val isPlayingState = !paused
                setPlaying(isPlayingState)

                val posObj = intent.extras?.get(PowerampAPI.Track.POSITION)
                var posSec = -1
                if (posObj != null) {
                    try {
                        posSec = when (posObj) {
                            is Number -> posObj.toInt()
                            is String -> posObj.toIntOrNull() ?: -1
                            else -> -1
                        }
                        if (posSec >= 0) {
                            updatePosition(posSec * 1000L)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing position extra in STATUS_CHANGED: ${e.message}", e)
                    }
                }
                Log.d(TAG, "STATUS_CHANGED: action=${intent.action} playing=$isPlayingState pos=$posSec")
            }
        }
    }

    private fun updatePosition(positionMs: Long) {
        _currentPositionMs.value = positionMs
        if (_isPlaying.value) {
            startTimeMs = System.currentTimeMillis()
            startPositionMs = positionMs
        }
    }

    override fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (playing) {
            startTimeMs = System.currentTimeMillis()
            startPositionMs = _currentPositionMs.value
        }
    }

    override fun seekTo(positionMs: Long) {
        updatePosition(positionMs)
    }

    override fun setDuration(durationMs: Long) {
        this.durationMs = durationMs
        if (_currentPositionMs.value > durationMs) {
            _currentPositionMs.value = 0L
        }
    }
}

class SimulatedPlaybackPositionSource(
    private val scope: CoroutineScope
) : PlaybackPositionSource {

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    override val isPlaying = _isPlaying.asStateFlow()

    private var durationMs = 240000L // default 4 mins
    private var tickerJob: Job? = null

    init {
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(100)
                if (_isPlaying.value) {
                    val nextPos = _currentPositionMs.value + 100
                    if (nextPos >= durationMs) {
                        _currentPositionMs.value = 0L // Loop
                    } else {
                        _currentPositionMs.value = nextPos
                    }
                }
            }
        }
    }

    override fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    override fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs.coerceIn(0L, durationMs)
    }

    override fun setDuration(durationMs: Long) {
        this.durationMs = durationMs
        if (_currentPositionMs.value > durationMs) {
            _currentPositionMs.value = 0L
        }
    }
}
