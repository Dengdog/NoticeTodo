package com.dengao.noticetodo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dengao.noticetodo.NoticeTodoApp
import com.dengao.noticetodo.domain.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DDL 前 3 小时的精确提醒。
 * Android 12+ 若没有精确闹钟权限，自动退回 setAndAllowWhileIdle（可能不准，但不崩溃）。
 * 任务完成/删除/DDL 变更时都要重新调用，避免残留闹钟。
 */
object ReminderScheduler {

    private const val AHEAD_MS = 3 * 60 * 60 * 1000L
    private const val REQUEST_BASE = 10_000

    fun scheduleFor(context: Context, task: Task) {
        val ddl = task.ddl ?: return
        if (task.isDone) {
            cancel(context, task.id)
            return
        }
        val triggerAt = ddl - AHEAD_MS
        if (triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, task.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, taskId))
    }

    /** 开机、应用更新、或用户手动改设置后，把所有未完成任务的提醒重排一遍。 */
    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val app = appContext as? NoticeTodoApp ?: return@launch
            runCatching {
                app.container.taskRepository.activeTasksOnce().forEach { scheduleFor(appContext, it) }
            }
        }
    }

    private fun pendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            (REQUEST_BASE + taskId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
