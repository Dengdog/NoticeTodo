package com.dengao.noticetodo.parser

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 纯本地 DDL 正则解析。规则全部写死在这里，避免误猜：
 * 1. 优先匹配带年份的完整日期：2026-03-01 / 2026/3/1 / 2026年3月1日
 * 2. 其次匹配“X月X日”
 * 3. 最后匹配相对日期：今天/明天/后天、本周X
 * 日期后 12 个字符内若出现 HH:mm，则使用该时刻，否则默认当天 23:59。
 * 解析不出来就返回 null（宁可留空，不猜）。
 */
object DdlExtractor {

    private val fullDateRegex = Regex("""(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})日?""")
    private val monthDayRegex = Regex("""(\d{1,2})月(\d{1,2})日""")
    private val timeRegex = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val relativeRegex = Regex("""(今天|明天|后天)""")
    private val weekDayRegex = Regex("""本周([一二三四五六日天])""")

    private const val DEFAULT_HOUR = 23
    private const val DEFAULT_MINUTE = 59

    fun extract(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (text.isBlank()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        val full = fullDateRegex.find(text)
        if (full != null) {
            val year = full.groupValues[1].toIntOrNull()
            val month = full.groupValues[2].toIntOrNull()
            val day = full.groupValues[3].toIntOrNull()
            if (year != null && month != null && day != null) {
                return build(year, month, day, timeNear(text, full.range.last), zone)
            }
        }

        val monthDay = monthDayRegex.find(text)
        if (monthDay != null) {
            val month = monthDay.groupValues[1].toIntOrNull()
            val day = monthDay.groupValues[2].toIntOrNull()
            if (month != null && day != null) {
                val time = timeNear(text, monthDay.range.last)
                var candidate = build(today.year, month, day, time, zone)
                if (candidate != null && candidate < now - 86_400_000L) {
                    candidate = build(today.year + 1, month, day, time, zone)
                }
                return candidate
            }
        }

        val relative = relativeRegex.find(text)
        if (relative != null) {
            val offset = when (relative.groupValues[1]) {
                "今天" -> 0L
                "明天" -> 1L
                else -> 2L
            }
            val date = today.plusDays(offset)
            val time = timeNear(text, relative.range.last) ?: (DEFAULT_HOUR to DEFAULT_MINUTE)
            return atTime(date, time.first, time.second, zone)
        }

        val weekDay = weekDayRegex.find(text)
        if (weekDay != null) {
            val target = weekDayValue(weekDay.groupValues[1])
            val current = today.dayOfWeek.value
            val delta = (target - current).let { if (it < 0) it + 7 else it }
            val date = today.plusDays(delta.toLong())
            val time = timeNear(text, weekDay.range.last) ?: (DEFAULT_HOUR to DEFAULT_MINUTE)
            return atTime(date, time.first, time.second, zone)
        }

        return null
    }

    private fun weekDayValue(label: String): Int = when (label) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        else -> 7
    }

    /** 在日期后面 12 个字符内找 HH:mm。 */
    private fun timeNear(text: String, endIndex: Int): Pair<Int, Int>? {
        val from = (endIndex + 1).coerceAtMost(text.length)
        val windowEnd = (from + 12).coerceAtMost(text.length)
        if (from >= windowEnd) return null
        val m = timeRegex.find(text.substring(from, windowEnd)) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun build(year: Int, month: Int, day: Int, time: Pair<Int, Int>?, zone: ZoneId): Long? {
        return try {
            val date = LocalDate.of(year, month, day)
            val t = time ?: (DEFAULT_HOUR to DEFAULT_MINUTE)
            atTime(date, t.first, t.second, zone)
        } catch (e: Exception) {
            null
        }
    }

    private fun atTime(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
