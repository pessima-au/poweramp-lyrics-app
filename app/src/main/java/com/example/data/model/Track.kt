package com.example.data.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String? = null,
    val status: TrackStatus = TrackStatus.NO_LYRICS,
    val path: String? = null
)

enum class TrackStatus {
    NO_LYRICS,
    CACHED,
    INSTRUMENTAL
}
