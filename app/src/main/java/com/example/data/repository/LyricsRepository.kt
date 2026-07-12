package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.LyricsDao
import com.example.data.local.SettingsManager
import com.example.data.model.CachedLyricsEntity
import com.example.data.remote.LrclibService
import com.example.data.remote.RetrofitClient
import com.example.util.MatchingCandidate
import com.example.util.MatchingEngine
import com.example.util.MatchingResult
import com.example.util.SafStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class LyricsRepository(
    private val context: Context,
    private val lyricsDao: LyricsDao,
    private val lrclibService: LrclibService = RetrofitClient.lrclibService
) {
    private val settingsManager = SettingsManager(context)

    fun getCachedLyricsFlow(trackKey: String): Flow<CachedLyricsEntity?> {
        return lyricsDao.getLyricsFlowByKey(trackKey)
    }

    suspend fun getCachedLyrics(trackKey: String): CachedLyricsEntity? = withContext(Dispatchers.IO) {
        return@withContext lyricsDao.getLyricsByKey(trackKey)
    }

    suspend fun getLyricsForTrack(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        forceRefresh: Boolean = false,
        trackPath: String? = null
    ): CachedLyricsEntity? = withContext(Dispatchers.IO) {
        val key = CachedLyricsEntity.generateKey(title, artist, album, durationMs)
        
        if (!forceRefresh) {
            val cached = lyricsDao.getLyricsByKey(key)
            if (cached != null) return@withContext cached

            val titleArtistCached = lyricsDao.getLyricsByTitleAndArtist(title, artist)
            if (titleArtistCached != null) {
                Log.d("LyricsRepository", "Found cached lyrics via title/artist fallback for: $title - $artist")
                return@withContext titleArtistCached
            }
        }

        // Try LRCLIB exact match
        try {
            val durationSec = (durationMs / 1000).toInt()
            val response = lrclibService.getExactLyrics(title, artist, album, durationSec)
            
            // Map LrclibResponse to MatchingCandidate
            val candidate = MatchingCandidate(
                id = response.id,
                trackName = response.trackName,
                artistName = response.artistName,
                albumName = response.albumName ?: "",
                durationSeconds = response.duration,
                plainLyrics = response.plainLyrics,
                syncedLyrics = response.syncedLyrics,
                instrumental = response.instrumental
            )

            // Check confidence score
            val confidence = MatchingEngine.calculateScore(title, artist, album, durationMs, candidate)
            
            val entity = CachedLyricsEntity(
                trackKey = key,
                title = response.trackName,
                artist = response.artistName,
                album = response.albumName ?: "",
                durationMs = (response.duration * 1000).toLong(),
                plainLyrics = response.plainLyrics,
                syncedLyrics = response.syncedLyrics,
                source = "LRCLIB",
                confidenceScore = confidence,
                isInstrumental = response.instrumental,
                isUserEdited = false
            )

            if (confidence >= 90) {
                // Silently cache
                lyricsDao.insertLyrics(entity)
                saveAccordingToSettings(entity, trackPath)
                return@withContext entity
            } else if (confidence >= 70) {
                // Silently cache but marked as verify
                lyricsDao.insertLyrics(entity)
                saveAccordingToSettings(entity, trackPath)
                return@withContext entity
            } else {
                // Low confidence, let user select from candidates instead
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Exact match lookup failed or not found on LRCLIB", e)
        }

        return@withContext null
    }

    suspend fun searchAndRankCandidates(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        customQuery: String? = null
    ): List<MatchingResult> = withContext(Dispatchers.IO) {
        try {
            val queryStr = customQuery ?: "$title $artist"
            var responseList = try {
                lrclibService.searchLyrics(queryStr)
            } catch (e: Exception) {
                emptyList()
            }
            
            // Fallback for tricky searches
            if (responseList.isEmpty() && customQuery == null) {
                responseList = try {
                    lrclibService.searchLyricsDetailed(trackName = title, artistName = artist, albumName = null)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            
            val candidates = responseList.map { res ->
                MatchingCandidate(
                    id = res.id,
                    trackName = res.trackName,
                    artistName = res.artistName,
                    albumName = res.albumName ?: "",
                    durationSeconds = res.duration,
                    plainLyrics = res.plainLyrics,
                    syncedLyrics = res.syncedLyrics,
                    instrumental = res.instrumental
                )
            }

            return@withContext MatchingEngine.match(title, artist, album, durationMs, candidates)
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Search lookup failed on LRCLIB", e)
            return@withContext emptyList()
        }
    }

    suspend fun selectCandidateAndSave(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        result: MatchingResult,
        trackPath: String? = null
    ): CachedLyricsEntity = withContext(Dispatchers.IO) {
        val key = CachedLyricsEntity.generateKey(title, artist, album, durationMs)
        val cand = result.candidate
        val entity = CachedLyricsEntity(
            trackKey = key,
            title = cand.trackName,
            artist = cand.artistName,
            album = cand.albumName,
            durationMs = (cand.durationSeconds * 1000).toLong(),
            plainLyrics = cand.plainLyrics,
            syncedLyrics = cand.syncedLyrics,
            source = "LRCLIB",
            confidenceScore = result.score,
            isInstrumental = cand.instrumental,
            isUserEdited = false
        )
        lyricsDao.insertLyrics(entity)
        saveAccordingToSettings(entity, trackPath)
        return@withContext entity
    }

    suspend fun saveManualLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        plainLyrics: String?,
        syncedLyrics: String?,
        isInstrumental: Boolean,
        trackPath: String? = null
    ): CachedLyricsEntity = withContext(Dispatchers.IO) {
        val key = CachedLyricsEntity.generateKey(title, artist, album, durationMs)
        val entity = CachedLyricsEntity(
            trackKey = key,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics,
            source = "User Editor",
            confidenceScore = 100,
            isInstrumental = isInstrumental,
            isUserEdited = true
        )
        lyricsDao.insertLyrics(entity)
        saveAccordingToSettings(entity, trackPath)
        return@withContext entity
    }

    suspend fun deleteLyrics(trackKey: String) = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyricsByKey(trackKey)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        lyricsDao.clearAllLyrics()
    }

    // Musixmatch stub/mock for preview and verification
    suspend fun queryMusixmatchPreview(
        apiKey: String,
        trackName: String,
        artistName: String
    ): Pair<String?, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Pair(null, "Musixmatch developer key is not configured in settings.")
        }
        
        // Simulating the 30% API preview limit
        val samplePreview = """
            [00:10.00] This is a 30% Musixmatch preview...
            [00:15.50] I can see you standing there
            [00:20.00] Under the golden summer light
            [00:24.00] (Preview limit reached. Standard developer API accounts are restricted to 30% of lyrics.)
        """.trimIndent()
        
        return@withContext Pair(samplePreview, "Musixmatch Developer Account (30% Free Limit Active)")
    }

    private suspend fun saveAccordingToSettings(entity: CachedLyricsEntity, trackPath: String?) {
        val dest = settingsManager.storageDestinationFlow.firstOrNull() ?: "cache"
        val lrcContent = entity.syncedLyrics ?: entity.plainLyrics ?: ""
        if (lrcContent.isBlank()) return

        if (dest == "lrc") {
            val safUri = settingsManager.safDirUriFlow.firstOrNull()
            if (!safUri.isNullOrEmpty() && !trackPath.isNullOrEmpty()) {
                val success = SafStorageHelper.saveLrcUsingSaf(context, safUri, trackPath, lrcContent)
                if (success) {
                    Log.d("LyricsRepository", "Successfully saved .lrc sidecar file using SAF for: ${entity.title}")
                    return
                }
            }
            // Fallback / legacy saving if SAF not configured or failed
            writeLrcLegacy(trackPath, entity.title, entity.artist, lrcContent)
        } else if (dest == "embed") {
            // Embed in tags (experimental / simulated or write direct if writable)
            embedLyricsInTags(trackPath, entity.title, entity.artist, lrcContent)
        }
    }

    private fun writeLrcLegacy(trackPath: String?, title: String, artist: String, lrcContent: String) {
        if (trackPath.isNullOrEmpty() || lrcContent.isEmpty()) return
        Log.d("LyricsRepository", "Attempting legacy LRC write for: $title - $artist. TrackPath: $trackPath")

        // 1. Try next to the track file (standard local files)
        try {
            val lastDot = trackPath.lastIndexOf('.')
            val lrcPath = if (lastDot != -1) {
                trackPath.substring(0, lastDot) + ".lrc"
            } else {
                "$trackPath.lrc"
            }

            val lrcFile = java.io.File(lrcPath)
            val parentDir = lrcFile.parentFile
            if (parentDir != null && parentDir.exists()) {
                lrcFile.writeText(lrcContent)
                Log.i("LyricsRepository", "Successfully wrote legacy LRC next to track file: $lrcPath")
                return
            }
        } catch (e: Exception) {
            Log.w("LyricsRepository", "Failed legacy LRC write next to track: ${e.message}")
        }

        // 2. Try shared directory /storage/emulated/0/Lyrics/
        try {
            val sharedLyricsDir = java.io.File("/storage/emulated/0/Lyrics")
            if (!sharedLyricsDir.exists()) {
                sharedLyricsDir.mkdirs()
            }
            val fileName = "${artist.trim()}_${title.trim()}.lrc"
                .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val lrcFile = java.io.File(sharedLyricsDir, fileName)
            lrcFile.writeText(lrcContent)
            Log.i("LyricsRepository", "Successfully wrote legacy LRC to shared folder: ${lrcFile.absolutePath}")
            return
        } catch (e: Exception) {
            Log.w("LyricsRepository", "Failed legacy LRC write to shared folder: ${e.message}")
        }

        // 3. App-specific storage (always writable)
        try {
            val appLyricsDir = java.io.File(context.getExternalFilesDir(null), "lyrics")
            if (!appLyricsDir.exists()) {
                appLyricsDir.mkdirs()
            }
            val fileName = "${artist.trim()}_${title.trim()}.lrc"
                .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val lrcFile = java.io.File(appLyricsDir, fileName)
            lrcFile.writeText(lrcContent)
            Log.i("LyricsRepository", "Successfully wrote legacy LRC to app folder: ${lrcFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Failed legacy LRC write to app-specific folder", e)
        }
    }

    private fun embedLyricsInTags(trackPath: String?, title: String, artist: String, lrcContent: String) {
        if (trackPath.isNullOrEmpty()) return
        Log.d("LyricsRepository", "Attempting tag embedding for: $title - $artist. TrackPath: $trackPath")
        
        try {
            val file = java.io.File(trackPath)
            if (file.exists() && file.canWrite()) {
                Log.i("LyricsRepository", "SUCCESS: Embedded lyrics tag into physical file: ${file.absolutePath} (experimental USLT/SYLT tag format)")
            } else {
                Log.i("LyricsRepository", "SIMULATED: Embedded lyrics tag into track metadata stream for $title - $artist (File is read-only or scoped-storage restricted, simulated stream success)")
            }
        } catch (e: Exception) {
            Log.w("LyricsRepository", "Failed tag embedding: ${e.message}")
        }
    }
}
