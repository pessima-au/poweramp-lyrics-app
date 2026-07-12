package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ScannedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM scanned_tracks ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<ScannedTrackEntity>>

    @Query("SELECT * FROM scanned_tracks ORDER BY title ASC")
    suspend fun getAllTracks(): List<ScannedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<ScannedTrackEntity>)

    @Query("DELETE FROM scanned_tracks")
    suspend fun clearAllTracks()
}
