package com.dengao.noticetodo

import android.app.Application
import com.dengao.noticetodo.notification.KeepAliveService
import com.dengao.noticetodo.notification.NotificationHelper
import com.dengao.noticetodo.notification.ReminderScheduler
import com.dengao.noticetodo.notification.SummaryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoticeTodoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        // 开机/更新后的兜底：AlarmManager 与 WorkManager 都重排一次，失败不影响启动
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            runCatching {
                if (container.settingsRepository.isSummaryEnabled()) {
                    SummaryScheduler.schedule(this@NoticeTodoApp, container.settingsRepository.currentSummaryMinutes())
                }
                ReminderScheduler.rescheduleAll(this@NoticeTodoApp)
                // App 一起来就把保活前台服务拉起，减少「被冻结后要打开 App 才弹窗」的窗口期
                if (container.settingsRepository.isKeepAlive()) {
                    KeepAliveService.start(this@NoticeTodoApp)
                }
            }
        }
    }
}
