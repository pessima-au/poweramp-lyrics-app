package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsManager
import com.example.data.repository.LyricsRepository
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

class LyricsRequestReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "LyricsRequestReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PowerampAPI.Lyrics.ACTION_NEED_LYRICS) {
            val realId = intent.getLongExtra(PowerampAPI.Track.REAL_ID, PowerampAPI.NO_ID)
            val title = intent.getStringExtra(PowerampAPI.Track.TITLE)
            if (realId == PowerampAPI.NO_ID || title.isNullOrEmpty()) {
                Log.e(TAG, "Failed to parse ACTION_NEED_LYRICS realId=$realId title=$title")
                return
            }

            val artist = intent.getStringExtra(PowerampAPI.Track.ARTIST) ?: ""
            val album = intent.getStringExtra(PowerampAPI.Track.ALBUM) ?: ""
            val durationMs = intent.getIntExtra(PowerampAPI.Track.DURATION_MS, 0).toLong()
            val trackPath = intent.getStringExtra(PowerampAPI.Track.PATH)

            Log.d(TAG, "onReceive: realId=$realId, title=$title, artist=$artist, album=$album, durationMs=$durationMs, trackPath=$trackPath")

            // Show status notification via Foreground Service
            LyricsService.startForLyricsRequest(context, title, artist)

            val db = AppDatabase.getDatabase(context.applicationContext)
            val lyricsRepository = LyricsRepository(context.applicationContext, db.lyricsDao())
            val settingsManager = SettingsManager(context.applicationContext)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Try exact match (checks cache first, LRCLIB second; saves automatically on fetch)
                    val lyricsEntity = lyricsRepository.getLyricsForTrack(title, artist, album, durationMs, trackPath = trackPath)
                    if (lyricsEntity != null) {
                        val lyricsText = lyricsEntity.syncedLyrics ?: lyricsEntity.plainLyrics
                        if (!lyricsText.isNullOrEmpty()) {
                            val responseIntent = Intent(PowerampAPI.Lyrics.ACTION_UPDATE_LYRICS)
                            responseIntent.putExtra(PowerampAPI.EXTRA_ID, realId)
                            responseIntent.putExtra(PowerampAPI.Lyrics.EXTRA_LYRICS, lyricsText)
                            responseIntent.putExtra(PowerampAPI.Lyrics.EXTRA_INFO_LINE, "Verse for Poweramp")
                            
                            PowerampAPIHelper.sendPAIntent(context, responseIntent)
                            Log.d(TAG, "Sent ACTION_UPDATE_LYRICS back to Poweramp for realId=$realId")
                            LyricsService.updateStatus(context, "Matched & Synced: $title")
                            return@launch
                        }
                    }
                    
                    // If exact match failed, try broader search ranking candidate list
                    val threshold = 70 // Default threshold
                    val candidates = lyricsRepository.searchAndRankCandidates(title, artist, album, durationMs)
                    val bestResult = candidates.firstOrNull()
                    if (bestResult != null && bestResult.score >= threshold) {
                        val cand = bestResult.candidate
                        val lyricsText = cand.syncedLyrics ?: cand.plainLyrics
                        if (!lyricsText.isNullOrEmpty()) {
                            // Cache the candidate and write sidecar/tags via repository
                            lyricsRepository.selectCandidateAndSave(title, artist, album, durationMs, bestResult, trackPath = trackPath)
                            
                            val responseIntent = Intent(PowerampAPI.Lyrics.ACTION_UPDATE_LYRICS)
                            responseIntent.putExtra(PowerampAPI.EXTRA_ID, realId)
                            responseIntent.putExtra(PowerampAPI.Lyrics.EXTRA_LYRICS, lyricsText)
                            responseIntent.putExtra(PowerampAPI.Lyrics.EXTRA_INFO_LINE, "Verse (Searched)")
                            
                            PowerampAPIHelper.sendPAIntent(context, responseIntent)
                            Log.d(TAG, "Sent searched lyrics back to Poweramp for realId=$realId with score ${bestResult.score}")
                            LyricsService.updateStatus(context, "Matched: $title (${bestResult.score}%)")
                            return@launch
                        }
                    }

                    // Send an empty response to inform Poweramp that no lyrics are available
                    val responseIntent = Intent(PowerampAPI.Lyrics.ACTION_UPDATE_LYRICS)
                    responseIntent.putExtra(PowerampAPI.EXTRA_ID, realId)
                    responseIntent.putExtra(PowerampAPI.Lyrics.EXTRA_LYRICS, null as String?)
                    PowerampAPIHelper.sendPAIntent(context, responseIntent)
                    Log.d(TAG, "No lyrics found for realId=$realId. Sent empty response.")
                    LyricsService.updateStatus(context, "No lyrics found for: $title")
                } catch (e: Exception) {
                    Log.e(TAG, "Error matching/downloading lyrics for $title", e)
                    LyricsService.updateStatus(context, "Failed lookup: $title")
                }
            }
        }
    }
}
