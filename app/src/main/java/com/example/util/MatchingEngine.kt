package com.example.util

import kotlin.math.abs

data class MatchingCandidate(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSeconds: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val instrumental: Boolean
)

data class MatchingResult(
    val candidate: MatchingCandidate,
    val score: Int, // 0 to 100
    val matchStatus: MatchStatus
)

enum class MatchStatus {
    AUTO_ACCEPT, // >= 90
    VERIFY,      // 70 - 89
    MANUAL       // < 70
}

object MatchingEngine {

    private val VERSION_TAGS = listOf(
        "live", "acoustic", "remastered", "remaster", "radio edit", "extended mix", 
        "extended", "mix", "deluxe", "feat", "featuring", "instrumental", "cover", "demo"
    )

    fun match(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        candidates: List<MatchingCandidate>
    ): List<MatchingResult> {
        return candidates.map { candidate ->
            val score = calculateScore(title, artist, album, durationMs, candidate)
            val status = when {
                score >= 90 -> MatchStatus.AUTO_ACCEPT
                score >= 70 -> MatchStatus.VERIFY
                else -> MatchStatus.MANUAL
            }
            MatchingResult(candidate, score, status)
        }.sortedByDescending { it.score }
    }

    fun calculateScore(
        queryTitle: String,
        queryArtist: String,
        queryAlbum: String,
        queryDurationMs: Long,
        candidate: MatchingCandidate
    ): Int {
        // 1. Normalize
        val (normQueryTitle, queryTags) = parseTitle(queryTitle)
        val (normCandTitle, candTags) = parseTitle(candidate.trackName)

        val normQueryArtist = normalizeText(queryArtist)
        val normCandArtist = normalizeText(candidate.artistName)

        // 2. String similarity on Core Title and Artist (weighted 60%)
        val titleSim = levenshteinSimilarity(normQueryTitle, normCandTitle)
        val artistSim = levenshteinSimilarity(normQueryArtist, normCandArtist)
        
        // Combine text similarity (Artist similarity is highly important)
        val textSimScore = (titleSim * 0.6 + artistSim * 0.4) * 60.0

        // 3. Duration Closeness (weighted 30%)
        val queryDurationSec = queryDurationMs / 1000.0
        val candDurationSec = candidate.durationSeconds
        val durationDiff = abs(queryDurationSec - candDurationSec)
        
        val durationScore = when {
            durationDiff <= 3.0 -> 30.0
            durationDiff <= 10.0 -> 30.0 - ((durationDiff - 3.0) / 7.0) * 15.0 // scale down to 15
            durationDiff <= 30.0 -> maxOf(0.0, 15.0 - ((durationDiff - 10.0) / 20.0) * 15.0)
            else -> 0.0
        }

        // 4. Version Tag Agreement (weighted 10%)
        // Check if query tags match candidate tags
        val tagScore = when {
            queryTags.isEmpty() && candTags.isEmpty() -> 10.0 // perfect agreement (studio/plain)
            queryTags.isNotEmpty() && candTags.isNotEmpty() -> {
                // calculate Jaccard index of tags
                val intersection = queryTags.intersect(candTags).size
                val union = queryTags.union(candTags).size
                if (union > 0) {
                    (intersection.toDouble() / union.toDouble()) * 10.0
                } else 10.0
            }
            else -> {
                // One has tag, other doesn't (e.g. query is Live, candidate is Studio or vice versa)
                // Minor mismatch
                3.0
            }
        }

        val totalScore = textSimScore + durationScore + tagScore
        return totalScore.toInt().coerceIn(0, 100)
    }

    private fun parseTitle(title: String): Pair<String, Set<String>> {
        val normalized = normalizeText(title)
        val foundTags = mutableSetOf<String>()
        var coreTitle = normalized

        for (tag in VERSION_TAGS) {
            if (normalized.contains(tag)) {
                foundTags.add(tag)
                // Remove from core title
                coreTitle = coreTitle.replace(tag, "").replace(Regex("\\s+"), " ").trim()
            }
        }

        return Pair(coreTitle, foundTags)
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[áàâäãå]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[íìîï]"), "i")
            .replace(Regex("[óòôöõø]"), "o")
            .replace(Regex("[úùûü]"), "u")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[ñ]"), "n")
            // strip punctuation but keep alphanumerics and spaces
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val len1 = s1.length
        val len2 = s2.length
        
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)
        
        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        val distance = prev[len2]
        val maxLength = maxOf(s1.length, s2.length)
        return (maxLength - distance).toDouble() / maxLength
    }
}
