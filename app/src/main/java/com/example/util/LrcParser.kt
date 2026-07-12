package com.example.util

import java.util.Locale

data class LrcLine(
    val timestampMs: Long,
    val text: String
) {
    val formattedTime: String
        get() {
            val totalSeconds = timestampMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hundredths = (timestampMs % 1000) / 10
            return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
        }
}

object LrcParser {
    // Regex for matching [01:23.45] or [01:23:45] or [01:23.456]
    private val timePattern = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    fun parse(lrcText: String?): List<LrcLine> {
        if (lrcText.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        
        lrcText.lineSequence().forEach { line ->
            val matches = timePattern.findAll(line).toList()
            if (matches.isNotEmpty()) {
                // Strip all time tags from the line to get the lyric text
                var lyricText = line
                matches.forEach { match ->
                    lyricText = lyricText.replace(match.value, "")
                }
                lyricText = lyricText.trim()

                // A single line could have multiple time tags, e.g. [00:10.00][00:20.00] Duplicate line
                matches.forEach { match ->
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues.getOrNull(3) ?: "00"
                    
                    val fraction = fractionStr.toLongOrNull() ?: 0L
                    val fractionMs = when (fractionStr.length) {
                        1 -> fraction * 100
                        2 -> fraction * 10
                        3 -> fraction
                        else -> fraction
                    }
                    
                    val totalMs = (min * 60 * 1000) + (sec * 1000) + fractionMs
                    lines.add(LrcLine(totalMs, lyricText))
                }
            }
        }
        
        return lines.sortedBy { it.timestampMs }
    }

    fun makeLrc(lines: List<LrcLine>): String {
        val sb = StringBuilder()
        lines.sortedBy { it.timestampMs }.forEach { line ->
            sb.append("[").append(line.formattedTime).append("] ").append(line.text).append("\n")
        }
        return sb.toString()
    }
}
