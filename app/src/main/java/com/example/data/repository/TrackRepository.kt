package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.LyricsDao
import com.example.data.local.TrackDao
import com.example.data.model.CachedLyricsEntity
import com.example.data.model.ScannedTrackEntity
import com.example.data.model.Track
import com.example.data.model.TrackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class TrackRepository(
    private val context: Context,
    private val lyricsDao: LyricsDao,
    private val trackDao: TrackDao
) {

    private val powerampAuthority = "com.maxmpz.audioplayer.data"

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

    suspend fun getTracks(useMockFallback: Boolean = false): List<Track> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<Track>()
        val seenPaths = mutableSetOf<String>()
        val seenTitlesAndArtists = mutableSetOf<Pair<String, String>>()

        // 1. Scan Poweramp tracks if available
        try {
            val contentUri = Uri.parse("content://$powerampAuthority/files")
            val cursor = context.contentResolver.query(
                contentUri,
                arrayOf("_id", "title", "artist", "album", "duration", "path"),
                null, null, null
            )
            
            if (cursor != null) {
                val idCol = cursor.getColumnIndex("_id")
                val titleCol = cursor.getColumnIndex("title")
                val artistCol = cursor.getColumnIndex("artist")
                val albumCol = cursor.getColumnIndex("album")
                val durationCol = cursor.getColumnIndex("duration")
                val pathCol = cursor.getColumnIndex("path")
                
                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "" else ""
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "" else ""
                    val album = if (albumCol >= 0) cursor.getString(albumCol) ?: "" else ""
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val path = if (pathCol >= 0) cursor.getString(pathCol) else null
                    
                    if (title.isNotEmpty()) {
                        val track = Track(id, title, artist, album, duration, null, TrackStatus.NO_LYRICS, path)
                        tracksList.add(track)
                        if (path != null) {
                            seenPaths.add(path)
                        }
                        seenTitlesAndArtists.add(Pair(title.lowercase(), artist.lowercase()))
                    }
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "Failed to query Poweramp Content Provider", e)
        }

        // 2. Read scanned standard MediaStore local music files from database cache
        try {
            var dbTracks = trackDao.getAllTracks()
            if (dbTracks.isEmpty()) {
                Log.d("TrackRepository", "Track database cache is empty. Running synchronous fallback scan...")
                val fallbackList = runSynchronousFallbackScan()
                if (fallbackList.isNotEmpty()) {
                    trackDao.insertTracks(fallbackList)
                    dbTracks = fallbackList
                }
            }
            
            dbTracks.forEach { dbTrack ->
                val id = dbTrack.id
                val title = dbTrack.title
                val artist = dbTrack.artist
                val album = dbTrack.album
                val duration = dbTrack.durationMs
                val path = dbTrack.path ?: ""
                
                val titleLower = title.lowercase()
                val artistLower = artist.lowercase()
                
                // Deduplicate if we already saw the same file path or title-artist combo
                if (!seenPaths.contains(path) && !seenTitlesAndArtists.contains(Pair(titleLower, artistLower))) {
                    tracksList.add(
                        Track(
                            id = 10000000L + id, // offset to avoid conflict with Poweramp IDs
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            albumArtUri = null,
                            status = TrackStatus.NO_LYRICS,
                            path = path
                        )
                    )
                    if (path.isNotEmpty()) {
                        seenPaths.add(path)
                    }
                    seenTitlesAndArtists.add(Pair(titleLower, artistLower))
                }
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "Error loading scanned tracks from local database", e)
        }

        // 3. Scan and auto-import local sidecar LRC/TXT lyric files
        tracksList.forEach { track ->
            val path = track.path
            if (!path.isNullOrEmpty()) {
                try {
                    val file = java.io.File(path)
                    val lastDot = path.lastIndexOf('.')
                    val lrcPath = if (lastDot != -1) path.substring(0, lastDot) + ".lrc" else "$path.lrc"
                    val txtPath = if (lastDot != -1) path.substring(0, lastDot) + ".txt" else "$path.txt"
                    
                    var localLrcContent: String? = null
                    val lrcFile = java.io.File(lrcPath)
                    val txtFile = java.io.File(txtPath)
                    
                    if (lrcFile.exists()) {
                        localLrcContent = lrcFile.readText()
                    } else if (txtFile.exists()) {
                        localLrcContent = txtFile.readText()
                    } else {
                        // Check standard shared lyrics directory /storage/emulated/0/Lyrics/
                        val cleanTitle = track.title.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                        val cleanArtist = track.artist.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                        val candidateFiles = listOf(
                            java.io.File("/storage/emulated/0/Lyrics/${cleanArtist}_${cleanTitle}.lrc"),
                            java.io.File("/storage/emulated/0/Lyrics/${cleanTitle}_${cleanArtist}.lrc"),
                            java.io.File("/storage/emulated/0/Lyrics/${cleanTitle}.lrc")
                        )
                        val foundFile = candidateFiles.firstOrNull { it.exists() }
                        if (foundFile != null) {
                            localLrcContent = foundFile.readText()
                        }
                    }
                    
                    if (!localLrcContent.isNullOrBlank()) {
                        val key = CachedLyricsEntity.generateKey(track.title, track.artist, track.album, track.durationMs)
                        val existing = lyricsDao.getLyricsByKey(key)
                        if (existing == null) {
                            val isSynced = localLrcContent.contains("[") && localLrcContent.contains("]")
                            val entity = CachedLyricsEntity(
                                trackKey = key,
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                durationMs = track.durationMs,
                                plainLyrics = if (!isSynced) localLrcContent else null,
                                syncedLyrics = if (isSynced) localLrcContent else null,
                                source = "Local File Scan",
                                confidenceScore = 100,
                                isInstrumental = false,
                                isUserEdited = false
                            )
                            lyricsDao.insertLyrics(entity)
                            Log.i("TrackRepository", "Auto-detected and imported local lyric file for ${track.title}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TrackRepository", "Error checking sidecar lyric files for path: $path", e)
                }
            }
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

    private suspend fun runSynchronousFallbackScan(): List<ScannedTrackEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScannedTrackEntity>()
        val validExtensions = setOf("mp3", "flac", "m4a", "ogg", "wav", "aac", "wma", "opus", "mka", "ape")
        try {
            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.MIME_TYPE
            )
            val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0 " +
                    "AND ${android.provider.MediaStore.Audio.Media.DURATION} >= 10000 " +
                    "AND (${android.provider.MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.mp3' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.flac' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.m4a' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.ogg' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.wav' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.aac' " +
                    "OR ${android.provider.MediaStore.Audio.Media.DATA} LIKE '%.opus')"
                    
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val pathCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: ""
                    val artist = cursor.getString(artistCol) ?: ""
                    val album = cursor.getString(albumCol) ?: ""
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(pathCol) ?: ""
                    
                    if (title.isNotEmpty() && path.isNotEmpty()) {
                        val ext = path.substringAfterLast('.', "").lowercase()
                        if (validExtensions.contains(ext)) {
                            val file = java.io.File(path)
                            if (file.exists()) {
                                list.add(
                                    ScannedTrackEntity(
                                        id = id,
                                        title = title,
                                        artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                                        album = if (album == "<unknown>") "Unknown Album" else album,
                                        durationMs = duration,
                                        path = path,
                                        isPoweramp = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "Error in synchronous fallback scan", e)
        }
        return@withContext list
    }
}
