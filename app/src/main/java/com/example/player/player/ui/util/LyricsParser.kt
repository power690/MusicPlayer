package com.example.player.ui.util

import android.util.Log
import java.io.*
import java.util.regex.Pattern

object LyricsParser {

    private const val TAG = "LyricsParser"

    data class LyricLine(val timeMs: Long, val text: String)

    private val lrcPattern = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

    fun parse(raw: String?): List<LyricLine>? {
        if (raw.isNullOrBlank()) return null

        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val result = mutableListOf<LyricLine>()

        var hasTimestamp = false
        for (line in lines) {
            val parsed = parseLine(line)
            if (parsed != null) {
                hasTimestamp = true
                result.addAll(parsed)
            } else if (hasTimestamp) {

                if (result.isNotEmpty() && line.isNotBlank()) {
                    val last = result.last()
                    result[result.lastIndex] = last.copy(
                        text = if (last.text.isEmpty()) line else "${last.text} $line"
                    )
                }
            }
        }

        if (hasTimestamp) {
            result.sortBy { it.timeMs }
            Log.d(TAG, "Parsed ${result.size} timed lyric lines")
            return result
        }

        val plainText = lines.joinToString("\n")
        if (plainText.isBlank()) return null
        Log.d(TAG, "No timestamps found, returning plain text lyrics")
        return listOf(LyricLine(0L, plainText))
    }

    private fun parseLine(line: String): List<LyricLine>? {
        val matcher = lrcPattern.matcher(line)
        val timestamps = mutableListOf<Long>()

        var lastEnd = 0

        while (matcher.find()) {
            val minutes = matcher.group(1)?.toLongOrNull() ?: continue
            val seconds = matcher.group(2)?.toLongOrNull() ?: continue
            val fraction = matcher.group(3)?.padEnd(3, '0')?.toLongOrNull() ?: 0

            val timeMs = (minutes * 60 + seconds) * 1000 + fraction
            timestamps.add(timeMs)
            lastEnd = matcher.end()
        }

        if (timestamps.isEmpty()) return null

        val remaining = line.substring(lastEnd).trim()

        val cleanText = remaining.replace(Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>"), "").trim()

        if (timestamps.size == 1) {
            return listOf(LyricLine(timestamps[0], cleanText))
        }

        return timestamps.map { LyricLine(it, cleanText) }
    }

    fun findCurrentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (lines.size == 1) return 0

        var low = 0
        var high = lines.size - 1
        var result = 0

        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result
    }

    fun loadLyricsForSong(embeddedLyrics: String?, filePath: String?): List<LyricLine>? {

        if (!embeddedLyrics.isNullOrBlank()) {
            val parsed = parse(embeddedLyrics)
            if (!parsed.isNullOrEmpty()) return parsed
        }

        if (!filePath.isNullOrBlank()) {
            try {
                val raw = LyricsExtractor.getLyricsContent(filePath)
                if (!raw.isNullOrBlank()) {
                    val parsed = parse(raw)
                    if (!parsed.isNullOrEmpty()) return parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "LyricsExtractor failed: ${e.message}")
            }
        }

        return null
    }
}
