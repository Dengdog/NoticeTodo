package com.dengao.noticetodo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dengao.noticetodo.NoticeTodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 精确闹钟触发：读取任务并发一条系统通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? NoticeTodoApp
                val task = app?.container?.taskRepository?.get(taskId)
                if (task != null && !task.isDone) {
                    NotificationHelper.postReminder(appContext, task)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
