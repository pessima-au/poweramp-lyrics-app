package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.model.ScannedTrackEntity
import java.io.File

class MediaScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("MediaScanWorker", "Starting high-performance background MediaStore scan...")
        setProgress(workDataOf("status" to "Scanning...", "progress" to 0))

        val db = AppDatabase.getDatabase(applicationContext)
        val trackDao = db.trackDao()

        val scannedTracks = mutableListOf<ScannedTrackEntity>()
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

            // High-performance selection optimization performed directly at SQLite/database layer:
            // 1. IS_MUSIC check
            // 2. Duration >= 10 seconds to filter out short UI sounds/notification tones
            // 3. MIME_TYPE starts with audio/ or data has common audio extensions
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

            applicationContext.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val pathCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)

                var count = 0
                val totalCount = cursor.count
                Log.d("MediaScanWorker", "Found $totalCount candidate files via optimized query.")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: ""
                    val artist = cursor.getString(artistCol) ?: ""
                    val album = cursor.getString(albumCol) ?: ""
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(pathCol) ?: ""

                    if (title.isNotEmpty() && path.isNotEmpty()) {
                        // Ensure only valid audio formats are added by verifying file extension
                        val ext = path.substringAfterLast('.', "").lowercase()
                        if (validExtensions.contains(ext)) {
                            // Extra validation: verify physical file exists to prevent stale MediaStore entries
                            val file = File(path)
                            if (file.exists()) {
                                scannedTracks.add(
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
                    count++
                    if (count % 20 == 0 || count == totalCount) {
                        val percentage = if (totalCount > 0) (count * 100 / totalCount) else 100
                        setProgress(workDataOf("status" to "Processing...", "progress" to percentage))
                    }
                }
            }

            Log.d("MediaScanWorker", "Scan complete. Inserting ${scannedTracks.size} valid tracks into local database.")
            
            // Clear previous scanned standard tracks and insert the new ones
            trackDao.clearAllTracks()
            if (scannedTracks.isNotEmpty()) {
                trackDao.insertTracks(scannedTracks)
            }

            setProgress(workDataOf("status" to "Scan Completed", "progress" to 100, "count" to scannedTracks.size))
            return Result.success(workDataOf("scanned_count" to scannedTracks.size))
        } catch (e: Exception) {
            Log.e("MediaScanWorker", "Failed to run background MediaStore scan", e)
            setProgress(workDataOf("status" to "Scan Failed", "progress" to 100))
            return Result.failure()
        }
    }
}
