package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val trackKey: String, // stable hash of title+artist+album+durationMs
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val source: String,
    val confidenceScore: Int,
    val isInstrumental: Boolean,
    val isUserEdited: Boolean,
    val fetchedAt: Long = System.currentTimeMillis(),
    val lastVerifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun generateKey(title: String, artist: String, album: String, durationMs: Long): String {
            val normalizedTitle = title.lowercase().trim()
            val normalizedArtist = artist.lowercase().trim()
            val normalizedAlbum = album.lowercase().trim()
            return "$normalizedTitle|$normalizedArtist|$normalizedAlbum|$durationMs"
        }
    }
}
