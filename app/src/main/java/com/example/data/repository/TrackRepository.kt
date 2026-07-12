package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.LyricsDao
import com.example.data.model.CachedLyricsEntity
import com.example.data.model.Track
import com.example.data.model.TrackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class TrackRepository(
    private val context: Context,
    private val lyricsDao: LyricsDao
) {

    private val powerampAuthority = "com.maxmpz.audioplayer.data"
    
    // Static mock tracks for Phase 1 demo/preview when Poweramp is not installed
    private val mockTracks = listOf(
        Track(1, "Blinding Lights", "The Weeknd", "After Hours", 200000, null, TrackStatus.NO_LYRICS),
        Track(2, "Shape of You", "Ed Sheeran", "Divide", 233000, null, TrackStatus.NO_LYRICS),
        Track(3, "Bohemian Rhapsody", "Queen", "A Night at the Opera", 354000, null, TrackStatus.NO_LYRICS),
        Track(4, "Stay", "The Kid LAROI & Justin Bieber", "F*CK LOVE 3: OVER YOU", 141000, null, TrackStatus.NO_LYRICS),
        Track(5, "Hotel California", "Eagles", "Hotel California", 390000, null, TrackStatus.NO_LYRICS),
        Track(6, "Weightless", "Marconi Union", "Ambient Transmissions Volume 2", 480000, null, TrackStatus.INSTRUMENTAL),
        Track(7, "Fake Song Nonexistent ABCXYZ", "Unknown Artist", "No Album", 120000, null, TrackStatus.NO_LYRICS)
    )

    fun isPowerampInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            // Check major poweramp packages
            val packages = listOf(
                "com.maxmpz.audioplayer",
                "com.maxmpz.audioplayer.unlock",
                "com.maxmpz.audioplayer.trial"
            )
            packages.any { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getTracks(useMockFallback: Boolean = true): List<Track> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<Track>()
        
        try {
            val contentUri = Uri.parse("content://$powerampAuthority/files")
            val cursor = context.contentResolver.query(
                contentUri,
                arrayOf("_id", "title", "artist", "album", "duration"),
                null, null, null
            )
            
            if (cursor != null) {
                val idCol = cursor.getColumnIndex("_id")
                val titleCol = cursor.getColumnIndex("title")
                val artistCol = cursor.getColumnIndex("artist")
                val albumCol = cursor.getColumnIndex("album")
                val durationCol = cursor.getColumnIndex("duration")
                
                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "" else ""
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "" else ""
                    val album = if (albumCol >= 0) cursor.getString(albumCol) ?: "" else ""
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    
                    if (title.isNotEmpty()) {
                        tracksList.add(
                            Track(id, title, artist, album, duration, null, TrackStatus.NO_LYRICS)
                        )
                    }
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "Failed to query Poweramp Content Provider, using fallbacks", e)
        }

        if (tracksList.isEmpty() && useMockFallback) {
            // Poweramp not installed or empty library, return mock tracks
            tracksList.addAll(mockTracks)
        }

        // Map status against local Room database cache
        return@withContext tracksList.map { track ->
            val key = CachedLyricsEntity.generateKey(track.title, track.artist, track.album, track.durationMs)
            val cached = lyricsDao.getLyricsByKey(key)
            val updatedStatus = when {
                cached?.isInstrumental == true -> TrackStatus.INSTRUMENTAL
                cached != null -> TrackStatus.CACHED
                track.status == TrackStatus.INSTRUMENTAL -> TrackStatus.INSTRUMENTAL
                else -> TrackStatus.NO_LYRICS
            }
            track.copy(status = updatedStatus)
        }
    }
}
