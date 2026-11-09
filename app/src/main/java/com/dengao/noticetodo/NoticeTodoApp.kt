package com.dengao.noticetodo

import android.app.Application
import com.dengao.noticetodo.notification.KeepAliveService
import com.dengao.noticetodo.notification.ListenerGuard
import com.dengao.noticetodo.notification.NotificationHelper
import com.dengao.noticetodo.notification.ReminderScheduler
import com.dengao.noticetodo.notification.SummaryScheduler
import com.dengao.noticetodo.notification.WatchdogScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NoticeTodoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        // 开机/更新/进程重启后的兜底：闹钟、汇总、保活、监听重绑都做一次，失败不影响启动
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
                // 看门狗：每 15 分钟检查监听/保活，被杀后能自己爬起来
                WatchdogScheduler.schedule(this@NoticeTodoApp)
                // 进程刚重启时监听往往是「已授权但未连接」，走完整修复流程
                delay(1_500)
                ListenerGuard.repair(this@NoticeTodoApp)
            }
        }
    }
}
