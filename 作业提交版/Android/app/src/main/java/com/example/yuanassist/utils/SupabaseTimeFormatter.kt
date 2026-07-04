package com.example.yuanassist.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SupabaseTimeFormatter {
    private val beijingTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
    private val outputLocale: Locale = Locale.CHINA
    private const val DISPLAY_PATTERN = "yyyy-MM-dd HH:mm"

    private val offsetPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd HH:mm:ss.SSSXXX",
        "yyyy-MM-dd HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss.SSSX",
        "yyyy-MM-dd HH:mm:ssX"
    )

    private val legacyUtcPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun formatToBeijing(rawTime: String?, fallback: String = "最近更新"): String {
        val timestamp = parseTimestamp(rawTime)
        if (timestamp <= 0L) {
            return rawTime?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        }
        return createFormatter(DISPLAY_PATTERN, beijingTimeZone).format(Date(timestamp))
    }

    fun parseTimestamp(rawTime: String?): Long {
        val normalized = rawTime?.trim()?.takeIf { it.isNotBlank() } ?: return 0L
        val sanitized = normalized.replace(Regex("(\\.\\d{3})\\d+"), "$1")

        offsetPatterns.forEach { pattern ->
            parseWithPattern(sanitized, pattern)?.let { return it }
        }

        legacyUtcPatterns.forEach { pattern ->
            parseWithPattern(sanitized, pattern, utcTimeZone)?.let { return it }
        }

        return 0L
    }

    private fun parseWithPattern(
        value: String,
        pattern: String,
        timeZone: TimeZone? = null
    ): Long? {
        return runCatching {
            createFormatter(pattern, timeZone).parse(value)?.time
        }.getOrNull()
    }

    private fun createFormatter(pattern: String, timeZone: TimeZone? = null): SimpleDateFormat {
        return SimpleDateFormat(pattern, outputLocale).apply {
            isLenient = false
            if (timeZone != null) {
                this.timeZone = timeZone
            }
        }
    }
}
