package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiTool(
    val googleSearch: GeminiGoogleSearch? = null
)

@JsonClass(generateAdapter = true)
class GeminiGoogleSearch

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

class GeminiClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun getSearchAssistLink(
        apiKey: String,
        trackName: String,
        artistName: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        val prompt = """
            Find a reliable source page for the lyrics of the song "$trackName" by "$artistName".
            Using Google Search grounding, find and verify the link to this song's lyrics on genius.com, musixmatch.com, or azlyrics.com.
            Provide the direct URL link in a clear, clickable format, and a brief explanation of how the user can copy the lyrics.
            Do not output the full lyrics text.
        """.trimIndent()

        val requestBodyObj = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            tools = listOf(GeminiTool(googleSearch = GeminiGoogleSearch()))
        )

        val jsonAdapter = moshi.adapter(GeminiRequest::class.java)
        val jsonRequest = jsonAdapter.toJson(requestBodyObj)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonRequest.toRequestBody(mediaType)

        // Using gemini-3.5-flash as specified in the gemini-api skill for basic text tasks
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiClient", "API call failed: ${response.code} ${response.message}")
                    return@withContext "Error: API call failed with code ${response.code}"
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val responseAdapter = moshi.adapter(GeminiResponse::class.java)
                val geminiResponse = responseAdapter.fromJson(bodyString)
                val textResponse = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                textResponse
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Exception during Gemini API call", e)
            "Error: ${e.message}"
        }
    }

    suspend fun cleanOrTranslateLyrics(
        apiKey: String,
        lyrics: String,
        action: String // "clean" or "translate_es", "translate_fr", "translate_ja", etc.
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || lyrics.isBlank()) return@withContext null

        val instruction = when {
            action == "clean" -> "Clean up the following lyrics. Correct obvious spelling errors, standardize formatting (add consistent line breaks), remove double chorus blocks or bracketed noise like [Intro] if it ruins readability. Keep only the lyrics."
            action.startsWith("translate_") -> {
                val targetLang = action.substringAfter("translate_").replaceFirstChar { it.uppercase() }
                "Translate the following lyrics to $targetLang. Maintain the layout, line-by-line structure, and poetic rhythm. Keep the translation literal and elegant. Do not output anything other than the translated lyrics."
            }
            else -> "Help refine formatting of the lyrics."
        }

        val prompt = "$instruction\n\nLyrics:\n$lyrics"

        val requestBodyObj = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        val jsonAdapter = moshi.adapter(GeminiRequest::class.java)
        val jsonRequest = jsonAdapter.toJson(requestBodyObj)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonRequest.toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Error: ${response.code}"
                val bodyString = response.body?.string() ?: return@withContext null
                val responseAdapter = moshi.adapter(GeminiResponse::class.java)
                val geminiResponse = responseAdapter.fromJson(bodyString)
                geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
