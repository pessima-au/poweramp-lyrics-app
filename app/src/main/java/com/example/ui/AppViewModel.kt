package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsManager
import com.example.data.model.CachedLyricsEntity
import com.example.data.model.Track
import com.example.data.model.TrackStatus
import com.example.data.remote.GeminiClient
import com.example.data.remote.LrclibResponse
import com.example.data.remote.RetrofitClient
import com.example.data.repository.LyricsRepository
import com.example.data.repository.TrackRepository
import com.example.util.LrcLine
import com.example.util.LrcParser
import com.example.util.MatchStatus
import com.example.util.MatchingCandidate
import com.example.util.MatchingEngine
import com.example.util.MatchingResult
import com.example.util.SimulatedPlaybackPositionSource
import com.example.util.PowerampPlaybackPositionSource
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log

class AppViewModel(
    application: Application,
    private val trackRepository: TrackRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {

    // Navigation state
    private val _currentScreen = MutableStateFlow<String>("library")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // Tracks & Library state
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _selectedTab = MutableStateFlow<String>("All")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _selectedTrack = MutableStateFlow<Track?>(null)
    val selectedTrack: StateFlow<Track?> = _selectedTrack.asStateFlow()

    private val _selectedTrackLyrics = MutableStateFlow<CachedLyricsEntity?>(null)
    val selectedTrackLyrics: StateFlow<CachedLyricsEntity?> = _selectedTrackLyrics.asStateFlow()

    private val _lrcLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lrcLines: StateFlow<List<LrcLine>> = _lrcLines.asStateFlow()

    private val _plainLyrics = MutableStateFlow<String>("")
    val plainLyrics: StateFlow<String> = _plainLyrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MatchingResult>>(emptyList())
    val searchResults: StateFlow<List<MatchingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isFineSearchMode = MutableStateFlow(false)
    val isFineSearchMode: StateFlow<Boolean> = _isFineSearchMode.asStateFlow()

    // Playback timeline source
    val playbackPositionSource = PowerampPlaybackPositionSource(application, viewModelScope)

    // Selection for batch downloads
    private val _selectedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTrackIds: StateFlow<Set<Long>> = _selectedTrackIds.asStateFlow()

    private val _batchDownloading = MutableStateFlow(false)
    val batchDownloading: StateFlow<Boolean> = _batchDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow("")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    // Palette API Extracted colors
    private val _extractedDominantColor = MutableStateFlow<Int?>(null)
    val extractedDominantColor: StateFlow<Int?> = _extractedDominantColor.asStateFlow()

    private val _extractedMutedColor = MutableStateFlow<Int?>(null)
    val extractedMutedColor: StateFlow<Int?> = _extractedMutedColor.asStateFlow()

    // Gemini API
    private val geminiClient = GeminiClient()
    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Settings flows
    val themeMode = settingsManager.themeModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val dynamicColor = settingsManager.dynamicColorFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val defaultLyricsType = settingsManager.defaultLyricsTypeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "synced")
    val fallbackBroaderSearch = settingsManager.fallbackBroaderSearchFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val wifiOnly = settingsManager.wifiOnlyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyFailure = settingsManager.notifyFailureFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val markInstrumental = settingsManager.markInstrumentalFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val storageDestination = settingsManager.storageDestinationFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "cache")
    val safDirUri = settingsManager.safDirUriFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val geminiApiKey = settingsManager.geminiApiKeyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val matchingThreshold = settingsManager.matchingThresholdFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 70)
    val activePreset = settingsManager.activePresetFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "Album Art Glow")
    val immersiveFontFamily = settingsManager.immersiveFontFamilyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "default")
    val immersiveFontSize = settingsManager.immersiveFontSizeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 24f)
    val immersiveAlignment = settingsManager.immersiveAlignmentFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "center")
    val immersiveTextShadow = settingsManager.immersiveTextShadowFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val userPresets = settingsManager.userPresetsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val floatingLyricsEnabled = settingsManager.floatingLyricsEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadTracks()
        observePowerampPlayback()
    }

    fun loadTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = trackRepository.getTracks()
                _tracks.value = list
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to load tracks", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }

    fun selectTrack(track: Track?) {
        _selectedTrack.value = track
        _selectedTrackLyrics.value = null
        _lrcLines.value = emptyList()
        _plainLyrics.value = ""
        _aiResult.value = null
        
        if (track != null) {
            playbackPositionSource.setDuration(track.durationMs)
            playbackPositionSource.seekTo(0)
            
            // Extract dominant and muted colors from album art URI
            updateExtractedColors(getApplication(), track.albumArtUri, track.title, track.artist)
            
            // Query local lyrics database
            viewModelScope.launch {
                val key = CachedLyricsEntity.generateKey(track.title, track.artist, track.album, track.durationMs)
                lyricsRepository.getCachedLyricsFlow(key).collectLatest { lyrics ->
                    _selectedTrackLyrics.value = lyrics
                    if (lyrics != null) {
                        _lrcLines.value = LrcParser.parse(lyrics.syncedLyrics)
                        _plainLyrics.value = lyrics.plainLyrics ?: ""
                    } else {
                        _lrcLines.value = emptyList()
                        _plainLyrics.value = ""
                    }
                }
            }
        }
    }

    fun updateExtractedColors(context: android.content.Context, uri: String?, title: String, artist: String) {
        viewModelScope.launch {
            if (uri != null) {
                try {
                    val loader = coil.ImageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(uri)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val drawable = result.drawable
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            val bitmap = drawable.bitmap
                            val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                            val dominant = palette.getDominantColor(0xFF312E3D.toInt())
                            val muted = palette.getMutedColor(0xFF1C1B1F.toInt())
                            _extractedDominantColor.value = dominant
                            _extractedMutedColor.value = muted
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to extract palette", e)
                }
            }
            
            // Fallback: Generate consistent colors based on title and artist
            val hash = (title + artist).hashCode()
            val r1 = ((hash and 0xFF0000) shr 16 or 0x30) and 0xFF
            val g1 = ((hash and 0x00FF00) shr 8 or 0x20) and 0xFF
            val b1 = (hash and 0x0000FF or 0x20) and 0xFF
            val dominantFallback = (0xFF shl 24) or (r1 shl 16) or (g1 shl 8) or b1
            
            val r2 = (((hash * 31) and 0xFF0000) shr 16 or 0x15) and 0xFF
            val g2 = (((hash * 31) and 0x00FF00) shr 8 or 0x15) and 0xFF
            val b2 = ((hash * 31) and 0x0000FF or 0x15) and 0xFF
            val mutedFallback = (0xFF shl 24) or (r2 shl 16) or (g2 shl 8) or b2
            
            _extractedDominantColor.value = dominantFallback
            _extractedMutedColor.value = mutedFallback
        }
    }

    fun saveUserPreset(name: String, startColorHex: String, endColorHex: String) {
        viewModelScope.launch {
            val current = settingsManager.userPresetsFlow.firstOrNull() ?: ""
            val newPreset = "$name|$startColorHex|$endColorHex"
            val updated = if (current.isEmpty()) newPreset else "$current;$newPreset"
            settingsManager.setUserPresets(updated)
        }
    }

    fun deleteUserPreset(name: String) {
        viewModelScope.launch {
            val current = settingsManager.userPresetsFlow.firstOrNull() ?: ""
            if (current.isNotEmpty()) {
                val updated = current.split(";")
                    .filter { !it.startsWith("$name|") }
                    .joinToString(";")
                settingsManager.setUserPresets(updated)
                if (activePreset.value == name) {
                    setActivePreset("Album Art Glow")
                }
            }
        }
    }

    // Toggle track selection for batch downloads
    fun toggleTrackSelection(trackId: Long) {
        val current = _selectedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _selectedTrackIds.value = current
    }

    fun clearTrackSelection() {
        _selectedTrackIds.value = emptySet()
    }

    // Auto-search / Exact match lookup
    fun fetchLyricsForCurrentTrack() {
        val track = _selectedTrack.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val lyrics = lyricsRepository.getLyricsForTrack(
                track.title, track.artist, track.album, track.durationMs, forceRefresh = true, trackPath = track.path
            )
            if (lyrics != null) {
                _selectedTrackLyrics.value = lyrics
                _lrcLines.value = LrcParser.parse(lyrics.syncedLyrics)
                _plainLyrics.value = lyrics.plainLyrics ?: ""
                loadTracks() // reload statuses
            } else {
                // Not found automatically or low confidence, trigger coarse/fine search modes
                setSearchQuery("${track.title} ${track.artist}")
                searchLyrics()
            }
            _isLoading.value = false
        }
    }

    // Search query management
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFineSearchMode(fine: Boolean) {
        _isFineSearchMode.value = fine
    }

    fun searchLyrics() {
        val track = _selectedTrack.value ?: return
        val query = _searchQuery.value
        viewModelScope.launch {
            _isSearching.value = true
            val results = lyricsRepository.searchAndRankCandidates(
                track.title, track.artist, track.album, track.durationMs, customQuery = query.ifBlank { null }
            )
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun selectCandidate(result: MatchingResult) {
        val track = _selectedTrack.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val entity = lyricsRepository.selectCandidateAndSave(
                track.title, track.artist, track.album, track.durationMs, result, trackPath = track.path
            )
            _selectedTrackLyrics.value = entity
            _lrcLines.value = LrcParser.parse(entity.syncedLyrics)
            _plainLyrics.value = entity.plainLyrics ?: ""
            loadTracks() // update library statuses
            _isLoading.value = false
        }
    }

    // Batch download using WorkManager
    fun startBatchDownloadSelected() {
        val selectedIds = _selectedTrackIds.value.toLongArray()
        if (selectedIds.isEmpty()) return

        // Start Foreground Service for progress and persistent notification
        com.example.LyricsService.startForBatchDownload(getApplication(), selectedIds)

        val isWifiOnly = wifiOnly.value
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(
                if (isWifiOnly) androidx.work.NetworkType.UNMETERED 
                else androidx.work.NetworkType.CONNECTED
            )
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.util.LyricsDownloadWorker>()
            .setInputData(androidx.work.workDataOf("track_ids" to selectedIds))
            .setConstraints(constraints)
            .build()

        val workManager = androidx.work.WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            "lyrics_batch_download",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Observe progress from WorkManager
        viewModelScope.launch {
            _batchDownloading.value = true
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    val progressData = workInfo.progress
                    val progressStr = progressData.getString("progress")
                    if (progressStr != null) {
                        _downloadProgress.value = progressStr
                    }
                    
                    when (workInfo.state) {
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            _downloadProgress.value = "Completed downloading ${selectedIds.size} tracks!"
                            _selectedTrackIds.value = emptySet()
                            loadTracks()
                            delay(2000)
                            _batchDownloading.value = false
                            _downloadProgress.value = ""
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            _downloadProgress.value = "Batch download failed"
                            delay(2000)
                            _batchDownloading.value = false
                            _downloadProgress.value = ""
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            _downloadProgress.value = "Batch download cancelled"
                            delay(2000)
                            _batchDownloading.value = false
                            _downloadProgress.value = ""
                        }
                        else -> {
                            // keep updating progress
                        }
                    }
                }
            }
        }
    }

    // Manual lyrics editor functions
    fun saveEditedLyrics(plain: String, synced: String, isInstrumental: Boolean) {
        val track = _selectedTrack.value ?: return
        viewModelScope.launch {
            val entity = lyricsRepository.saveManualLyrics(
                track.title, track.artist, track.album, track.durationMs,
                plainLyrics = plain.ifBlank { null },
                syncedLyrics = synced.ifBlank { null },
                isInstrumental = isInstrumental,
                trackPath = track.path
            )
            _selectedTrackLyrics.value = entity
            _lrcLines.value = LrcParser.parse(entity.syncedLyrics)
            _plainLyrics.value = entity.plainLyrics ?: ""
            loadTracks()
        }
    }

    // Nudge timestamps
    fun nudgeTimestamp(index: Int, deltaMs: Long) {
        val currentLines = _lrcLines.value.toMutableList()
        if (index in currentLines.indices) {
            val line = currentLines[index]
            val newTime = (line.timestampMs + deltaMs).coerceAtLeast(0L)
            currentLines[index] = line.copy(timestampMs = newTime)
            _lrcLines.value = currentLines.sortedBy { it.timestampMs }
            // Auto format back to LRC and update plain/synced
            val syncedLrc = LrcParser.makeLrc(_lrcLines.value)
            saveEditedLyrics(_plainLyrics.value, syncedLrc, false)
        }
    }

    // Re-sync a line to current playback position
    fun syncLineToCurrentPosition(index: Int) {
        val currentLines = _lrcLines.value.toMutableList()
        if (index in currentLines.indices) {
            val line = currentLines[index]
            val currentPos = playbackPositionSource.currentPositionMs.value
            currentLines[index] = line.copy(timestampMs = currentPos)
            _lrcLines.value = currentLines.sortedBy { it.timestampMs }
            val syncedLrc = LrcParser.makeLrc(_lrcLines.value)
            saveEditedLyrics(_plainLyrics.value, syncedLrc, false)
        }
    }

    // Update a line's text and/or timestamp directly
    fun updateLrcLine(index: Int, newText: String, newTimestampMs: Long) {
        val currentLines = _lrcLines.value.toMutableList()
        if (index in currentLines.indices) {
            currentLines[index] = LrcLine(timestampMs = newTimestampMs.coerceAtLeast(0L), text = newText)
            _lrcLines.value = currentLines.sortedBy { it.timestampMs }
            val syncedLrc = LrcParser.makeLrc(_lrcLines.value)
            saveEditedLyrics(_plainLyrics.value, syncedLrc, false)
        }
    }

    // Gemini API Search Assist
    fun requestSearchAssist() {
        val track = _selectedTrack.value ?: return
        val key = geminiApiKey.value
        if (key.isBlank()) {
            _aiResult.value = "Error: Please enter your Gemini API Key in Settings to enable Search Assist."
            return
        }

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = "Asking Gemini AI to search for source pages..."
            val result = geminiClient.getSearchAssistLink(key, track.title, track.artist)
            _aiResult.value = result ?: "No candidate source links found by AI."
            _isAiLoading.value = false
        }
    }

    // Gemini AI Translation / Clean up
    fun requestAiLyricsTask(action: String) {
        val lyricsText = _plainLyrics.value
        if (lyricsText.isBlank()) {
            _aiResult.value = "Error: Plain lyrics text is empty. Fetch or enter lyrics first."
            return
        }
        val key = geminiApiKey.value
        if (key.isBlank()) {
            _aiResult.value = "Error: Please enter your Gemini API Key in Settings to enable AI Assist."
            return
        }

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = "Processing with Gemini AI..."
            val result = geminiClient.cleanOrTranslateLyrics(key, lyricsText, action)
            _aiResult.value = result
            _isAiLoading.value = false
        }
    }

    fun acceptAiResultAsLyrics() {
        val result = _aiResult.value ?: return
        if (result.startsWith("Error:") || result.startsWith("Asking") || result.startsWith("Processing")) return
        
        // Save to editor
        _plainLyrics.value = result
        val synced = LrcParser.makeLrc(_lrcLines.value)
        saveEditedLyrics(result, synced, false)
        _aiResult.value = null
    }

    fun clearAiResult() {
        _aiResult.value = null
    }

    // Settings adjustments
    fun setThemeMode(mode: String) = viewModelScope.launch { settingsManager.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsManager.setDynamicColor(enabled) }
    fun setDefaultLyricsType(type: String) = viewModelScope.launch { settingsManager.setDefaultLyricsType(type) }
    fun setFallbackBroaderSearch(enabled: Boolean) = viewModelScope.launch { settingsManager.setFallbackBroaderSearch(enabled) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settingsManager.setWifiOnly(enabled) }
    fun setNotifyFailure(enabled: Boolean) = viewModelScope.launch { settingsManager.setNotifyFailure(enabled) }
    fun setMarkInstrumental(enabled: Boolean) = viewModelScope.launch { settingsManager.setMarkInstrumental(enabled) }
    fun setStorageDestination(dest: String) = viewModelScope.launch { settingsManager.setStorageDestination(dest) }
    fun setSafDirUri(uriStr: String?) = viewModelScope.launch { settingsManager.setSafDirUri(uriStr) }
    fun setGeminiApiKey(key: String) = viewModelScope.launch { settingsManager.setGeminiApiKey(key) }
    fun setMatchingThreshold(threshold: Int) = viewModelScope.launch { settingsManager.setMatchingThreshold(threshold) }
    fun setActivePreset(preset: String) = viewModelScope.launch { settingsManager.setActivePreset(preset) }
    fun setImmersiveFontFamily(family: String) = viewModelScope.launch { settingsManager.setImmersiveFontFamily(family) }
    fun setImmersiveFontSize(size: Float) = viewModelScope.launch { settingsManager.setImmersiveFontSize(size) }
    fun setImmersiveAlignment(alignment: String) = viewModelScope.launch { settingsManager.setImmersiveAlignment(alignment) }
    fun setImmersiveTextShadow(enabled: Boolean) = viewModelScope.launch { settingsManager.setImmersiveTextShadow(enabled) }
    
    fun forcePosSync() {
        try {
            PowerampAPIHelper.sendPAIntent(
                getApplication(),
                android.content.Intent(PowerampAPI.ACTION_API_COMMAND)
                    .putExtra(PowerampAPI.EXTRA_COMMAND, PowerampAPI.Commands.POS_SYNC)
            )
            Log.d("AppViewModel", "Manual POS_SYNC command broadcast sent to Poweramp")
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to send manual POS_SYNC command to Poweramp", e)
        }
    }
    
    fun setFloatingLyricsEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsManager.setFloatingLyricsEnabled(enabled)
        if (enabled) {
            if (android.provider.Settings.canDrawOverlays(getApplication())) {
                com.example.FloatingLyricsOverlayService.start(getApplication())
            }
        } else {
            com.example.FloatingLyricsOverlayService.stop(getApplication())
        }
    }

    fun clearCache() = viewModelScope.launch {
        lyricsRepository.clearCache()
        loadTracks()
    }

    fun deleteLyrics(trackKey: String) {
        viewModelScope.launch {
            lyricsRepository.deleteLyrics(trackKey)
            loadTracks()
        }
    }

    private fun observePowerampPlayback() {
        viewModelScope.launch {
            combine(
                playbackPositionSource.currentTrackTitle,
                playbackPositionSource.currentTrackArtist,
                playbackPositionSource.currentTrackAlbum
            ) { title, artist, album ->
                Triple(title, artist, album)
            }.collectLatest { (title, artist, album) ->
                if (title.isNotEmpty()) {
                    val existingTrack = _tracks.value.find { 
                        it.title.equals(title, ignoreCase = true) && 
                        it.artist.equals(artist, ignoreCase = true) 
                    }
                    if (existingTrack != null) {
                        selectTrack(existingTrack)
                    } else {
                        val tempTrack = Track(
                            id = -1,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = 240000,
                            status = TrackStatus.NO_LYRICS,
                            albumArtUri = null
                        )
                        selectTrack(tempTrack)
                    }
                }
            }
        }
    }

    fun handleLyricsLinkIntent(title: String, artist: String, album: String, durationMs: Long) {
        viewModelScope.launch {
            val existingTrack = _tracks.value.find { 
                it.title.equals(title, ignoreCase = true) && 
                it.artist.equals(artist, ignoreCase = true) 
            }
            if (existingTrack != null) {
                selectTrack(existingTrack)
            } else {
                val tempTrack = Track(
                    id = -1,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = if (durationMs > 0) durationMs else 240000,
                    status = TrackStatus.NO_LYRICS,
                    albumArtUri = null
                )
                selectTrack(tempTrack)
            }
            navigateTo("immersive_lyrics")
        }
    }

    override fun onCleared() {
        playbackPositionSource.unregister()
        super.onCleared()
    }
}

class AppViewModelFactory(
    private val application: Application,
    private val trackRepository: TrackRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(application, trackRepository, lyricsRepository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
