package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CachedLyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics ORDER BY fetchedAt DESC")
    fun getAllLyricsFlow(): Flow<List<CachedLyricsEntity>>

    @Query("SELECT * FROM cached_lyrics WHERE trackKey = :trackKey LIMIT 1")
    fun getLyricsFlowByKey(trackKey: String): Flow<CachedLyricsEntity?>

    @Query("SELECT * FROM cached_lyrics WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getLyricsByKey(trackKey: String): CachedLyricsEntity?

    @Query("SELECT * FROM cached_lyrics WHERE LOWER(title) = LOWER(:title) AND LOWER(artist) = LOWER(:artist) LIMIT 1")
    suspend fun getLyricsByTitleAndArtist(title: String, artist: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: CachedLyricsEntity)

    @Query("DELETE FROM cached_lyrics WHERE trackKey = :trackKey")
    suspend fun deleteLyricsByKey(trackKey: String)

    @Query("DELETE FROM cached_lyrics")
    suspend fun clearAllLyrics()
}
