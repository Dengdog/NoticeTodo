package com.dengao.noticetodo.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dengao.noticetodo.NoticeTodoApp

/** 每日汇总：统计今日到期/已完成/逾期，推一条通知。 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NoticeTodoApp ?: return Result.success()
        val settings = app.container.settingsRepository
        if (!settings.isSummaryEnabled()) return Result.success()
        val repository = app.container.taskRepository
        val now = System.currentTimeMillis()
        val stats = repository.todayStats(now)
        val overdueTitles = repository.activeTasksOnce()
            .filter { it.ddl != null && it.ddl < now }
            .sortedBy { it.ddl }
            .map { it.title }
        NotificationHelper.postSummary(applicationContext, stats, overdueTitles)
        return Result.success()
    }
}
