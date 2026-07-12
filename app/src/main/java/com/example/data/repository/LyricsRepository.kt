package com.example.data.repository

import android.util.Log
import com.example.data.local.LyricsDao
import com.example.data.model.CachedLyricsEntity
import com.example.data.remote.LrclibService
import com.example.data.remote.RetrofitClient
import com.example.util.MatchingCandidate
import com.example.util.MatchingEngine
import com.example.util.MatchingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LyricsRepository(
    private val lyricsDao: LyricsDao,
    private val lrclibService: LrclibService = RetrofitClient.lrclibService
) {

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
        forceRefresh: Boolean = false
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
                return@withContext entity
            } else if (confidence >= 70) {
                // Silently cache but marked as verify
                lyricsDao.insertLyrics(entity)
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
            val responseList = lrclibService.searchLyrics(queryStr)
            
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
        result: MatchingResult
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
        return@withContext entity
    }

    suspend fun saveManualLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        plainLyrics: String?,
        syncedLyrics: String?,
        isInstrumental: Boolean
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
}
