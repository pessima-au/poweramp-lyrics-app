package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.repository.LyricsRepository
import com.example.data.repository.TrackRepository

class LyricsDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val trackIds = inputData.getLongArray("track_ids") ?: return Result.failure()
        if (trackIds.isEmpty()) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val lyricsDao = db.lyricsDao()
        val trackRepository = TrackRepository(applicationContext, lyricsDao)
        val lyricsRepository = LyricsRepository(applicationContext, lyricsDao)

        val total = trackIds.size
        var completedCount = 0

        // Fetch tracks from repository
        val allTracks = trackRepository.getTracks(useMockFallback = true)
        val tracksToDownload = allTracks.filter { trackIds.contains(it.id) }

        for (track in tracksToDownload) {
            val progress = "Downloading (${completedCount + 1}/$total): ${track.title}"
            setProgress(workDataOf("progress" to progress, "completed" to completedCount, "total" to total))
            
            try {
                lyricsRepository.getLyricsForTrack(
                    track.title, track.artist, track.album, track.durationMs, forceRefresh = true
                )
            } catch (e: Exception) {
                Log.e("LyricsDownloadWorker", "Failed to download lyrics for ${track.title}", e)
            }
            completedCount++
        }

        setProgress(workDataOf("progress" to "Completed downloading $total tracks!", "completed" to total, "total" to total))
        return Result.success()
    }
}
