package com.dengao.noticetodo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 主页分组：逾期 / 今天 / 本周 / 以后。 */
enum class TaskBucket(val label: String) {
    OVERDUE("逾期"),
    TODAY("今天"),
    THIS_WEEK("本周"),
    LATER("以后")
}

object TaskBuckets {

    fun bucketOf(task: Task, now: Long, zone: ZoneId = ZoneId.systemDefault()): TaskBucket {
        val ddl = task.ddl ?: return TaskBucket.LATER
        if (ddl < now) return TaskBucket.OVERDUE
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val ddlDate: LocalDate = Instant.ofEpochMilli(ddl).atZone(zone).toLocalDate()
        if (ddlDate == today) return TaskBucket.TODAY
        // 本周 = 到今天所在自然周的周日结束；超过则归入“以后”
        val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong())
        return if (!ddlDate.isAfter(endOfWeek)) TaskBucket.THIS_WEEK else TaskBucket.LATER
    }

    /**
     * 已完成比例：从创建时间到 DDL 线性推进，DDL 越近越满，逾期后固定为 1。
     * 返回 null 表示“无 DDL”，UI 显示灰色占位。
     */
    fun progressOf(task: Task, now: Long): Float? {
        val ddl = task.ddl ?: return null
        val total = ddl - task.createdAt
        if (total <= 0L) return 1f
        val elapsed = now - task.createdAt
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
