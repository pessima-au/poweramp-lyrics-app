package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_tracks")
data class ScannedTrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String?,
    val isPoweramp: Boolean = false,
    val scannedAt: Long = System.currentTimeMillis()
)
