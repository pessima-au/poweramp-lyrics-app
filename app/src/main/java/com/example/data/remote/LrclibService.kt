package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LrclibResponse(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

interface LrclibService {
    @GET("api/get")
    suspend fun getExactLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") durationSeconds: Int,
        @Header("User-Agent") userAgent: String = "VerseForPoweramp/1.0 (albertpessima@gmail.com; Standalone Lyrics Companion)"
    ): LrclibResponse

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String,
        @Header("User-Agent") userAgent: String = "VerseForPoweramp/1.0 (albertpessima@gmail.com; Standalone Lyrics Companion)"
    ): List<LrclibResponse>

    @GET("api/search")
    suspend fun searchLyricsDetailed(
        @Query("track_name") trackName: String?,
        @Query("artist_name") artistName: String?,
        @Query("album_name") albumName: String?,
        @Header("User-Agent") userAgent: String = "VerseForPoweramp/1.0 (albertpessima@gmail.com; Standalone Lyrics Companion)"
    ): List<LrclibResponse>
}
