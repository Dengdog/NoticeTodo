package com.dengao.noticetodo.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dengao.noticetodo.NoticeTodoApp
import com.dengao.noticetodo.util.Diag
import java.util.concurrent.TimeUnit

/**
 * 看门狗：每 15 分钟检查一次
 * 1. 通知监听是否还连着，没连上就 requestRebind；
 * 2. 保活前台服务是否还在，不在就拉起来。
 *
 * 用途：App 被「上滑清理」杀掉后，WorkManager 仍会按时把进程唤醒，从而自救。
 */
class ListenerGuardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val app = appContext as? NoticeTodoApp ?: return Result.success()
        runCatching {
            ListenerGuard.repair(appContext)
            if (app.container.settingsRepository.isKeepAlive()) {
                KeepAliveService.start(appContext)
            }
            Diag.log(appContext, "guard", "watchdog 检查完成 connected=${ListenerGuard.isConnected()}")
        }
        return Result.success()
    }
}

object WatchdogScheduler {

    private const val WORK_NAME = "listener_watchdog"
    private const val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ListenerGuardWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
