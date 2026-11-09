package com.dengao.noticetodo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dengao.noticetodo.NoticeTodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机 / 应用更新后重排闹钟与每日汇总（AlarmManager 不会自己跨重启保留）。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        ReminderScheduler.rescheduleAll(appContext)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? NoticeTodoApp ?: return@launch
                val settings = app.container.settingsRepository
                if (settings.isSummaryEnabled()) {
                    SummaryScheduler.schedule(appContext, settings.currentSummaryMinutes())
                }
                if (settings.isKeepAlive()) {
                    KeepAliveService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
