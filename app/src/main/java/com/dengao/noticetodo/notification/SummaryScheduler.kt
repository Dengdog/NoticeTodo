package com.dengao.noticetodo.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 每日固定时间汇总（WorkManager）。
 * 用 24 小时周期 + 首次延迟对齐到下一个触发点；时间改了就用 UPDATE 策略替换。
 */
object SummaryScheduler {

    private const val WORK_NAME = "daily_summary"

    fun schedule(context: Context, minutesOfDay: Int) {
        val delayMinutes = minutesUntil(minutesOfDay)
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun minutesUntil(minutesOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val target = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        var next: LocalDateTime = LocalDateTime.of(now.toLocalDate(), target)
        if (!next.isAfter(now.toLocalDateTime())) {
            next = next.plusDays(1)
        }
        return Duration.between(now.toLocalDateTime(), next).toMinutes().coerceAtLeast(1)
    }
}
