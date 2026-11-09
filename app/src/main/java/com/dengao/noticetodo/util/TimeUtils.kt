package com.dengao.noticetodo.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun startOfDay(ts: Long): Long =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfNextDay(ts: Long): Long =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun localDate(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    fun atTime(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    fun formatDdl(ts: Long?): String {
        if (ts == null) return "未设置 DDL"
        val zoned = Instant.ofEpochMilli(ts).atZone(zone)
        val today = LocalDate.now(zone)
        return when {
            zoned.toLocalDate() == today -> "今天 " + zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
            zoned.toLocalDate() == today.plusDays(1) -> "明天 " + zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
            zoned.toLocalDate() == today.minusDays(1) -> "昨天 " + zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
            else -> zoned.format(dateTimeFormatter)
        }
    }

    fun formatFull(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(zone).format(fullFormatter)

    fun formatDateShort(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(dateFormatter)

    /** 逾期天数（向上取整，最小 1）。 */
    fun overdueDays(ddl: Long, now: Long): Int {
        val diff = now - ddl
        if (diff <= 0) return 0
        return ((diff + 86_400_000L - 1) / 86_400_000L).toInt().coerceAtLeast(1)
    }

    /** 距离 DDL 的可读文本，用于卡片副标题。 */
    fun remainingText(ddl: Long?, now: Long): String {
        if (ddl == null) return "无 DDL"
        val diff = ddl - now
        if (diff <= 0) return "已逾期 ${overdueDays(ddl, now)} 天"
        val hours = diff / 3_600_000L
        return when {
            hours >= 48 -> "${hours / 24} 天后"
            hours >= 1 -> "${hours} 小时后"
            else -> "${(diff / 60_000L).coerceAtLeast(1)} 分钟后"
        }
    }
}
