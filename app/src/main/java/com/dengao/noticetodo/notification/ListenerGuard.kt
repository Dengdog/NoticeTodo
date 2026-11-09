package com.dengao.noticetodo.notification

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.dengao.noticetodo.util.Diag
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 监听连接守卫。
 *
 * 背景：进程被 ROM 的「上滑清理 / 一键清理」杀掉后，Android 会因为有 START_STICKY 前台服务
 * 把进程拉起来，但部分 ROM（实测 HyperOS / Android 17）**不会自动把 NotificationListenerService 重新绑上**，
 * 而且 App 主动调用 requestRebind 也会被系统忽略。结果是进程活着、通知却收不到，
 * 用户看到的现象就是「不弹窗，打开 App 才好」。
 *
 * 三级修复：
 * 1. requestRebind（标准 API，AOSP 可用）；
 * 2. 若 2 秒后仍未连接，且有 WRITE_SECURE_SETTINGS（adb 一次性授权），
 *    自己把 enabled_notification_listeners 关一次再开，强制系统重绑；
 * 3. 都失败就写日志，设置页会显示「已授权但未连接」。
 */
object ListenerGuard {

    private const val SETTING_KEY = "enabled_notification_listeners"
    private const val PREFS = "listener_guard"
    private const val KEY_BACKUP = "enabled_listeners_backup"
    private val connected = AtomicBoolean(false)
    private val repairMutex = Mutex()

    fun onConnected() {
        connected.set(true)
        Diag.log(null, "guard", "listener connected")
    }

    fun onDisconnected() {
        connected.set(false)
        Diag.log(null, "guard", "listener disconnected")
    }

    fun isConnected(): Boolean = connected.get()

    /**
     * 深度修复会先「关掉」监听再「打开」。如果进程恰好在这中间被杀，
     * 监听就会停在关闭状态，所以先把原值备份进 SharedPreferences，
     * 下次启动发现我们的条目丢了就还原。
     */
    fun restoreIfInterrupted(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val backup = prefs.getString(KEY_BACKUP, null) ?: return
        val current = Settings.Secure.getString(appContext.contentResolver, SETTING_KEY).orEmpty()
        val component = ComponentName(appContext, WeChatNotificationListener::class.java).flattenToString()
        if (backup.contains(component, ignoreCase = true) && !current.contains(component, ignoreCase = true)) {
            runCatching {
                Settings.Secure.putString(appContext.contentResolver, SETTING_KEY, backup)
                Diag.log(appContext, "guard", "检测到上次修复中断，已还原通知使用权")
            }
        }
        prefs.edit().remove(KEY_BACKUP).apply()
    }

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun canDeepRepair(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** 标准重绑请求：便宜，先试这个。 */
    fun ensureBound(context: Context) {
        val appContext = context.applicationContext
        if (!isGranted(appContext)) {
            Diag.log(appContext, "guard", "没有通知使用权，跳过重绑")
            return
        }
        if (connected.get()) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(appContext, WeChatNotificationListener::class.java)
            )
            Diag.log(appContext, "guard", "监听未连接，已请求 requestRebind")
        }.onFailure {
            Diag.log(appContext, "guard", "requestRebind 失败：${it.javaClass.simpleName}: ${it.message}")
        }
    }

    /** 带兜底的完整修复流程，必须在后台线程调用。同一时刻只允许一个修复在跑。 */
    suspend fun repair(context: Context) = repairMutex.withLock {
        val appContext = context.applicationContext
        restoreIfInterrupted(appContext)
        if (connected.get()) return@withLock
        ensureBound(appContext)
        if (awaitConnected(2_000)) return@withLock
        if (!isGranted(appContext)) return@withLock
        if (!canDeepRepair(appContext)) {
            Diag.log(appContext, "guard", "requestRebind 无效且无 WRITE_SECURE_SETTINGS，跳过深度修复")
            return@withLock
        }
        toggleListenerSetting(appContext)
        val ok = awaitConnected(10_000)
        Diag.log(appContext, "guard", "深度修复后 connected=$ok")
        if (!ok) NotificationHelper.postListenerDown(appContext)
    }

    private suspend fun awaitConnected(timeoutMs: Long): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            if (connected.get()) return true
            delay(250)
            waited += 250
        }
        return connected.get()
    }

    /**
     * 把 enabled_notification_listeners 里我们的组件先摘掉再写回。
     * 这会触发系统「禁用 → 启用」，从而强制重新绑定监听。
     * 只改动我们自己的条目，其他监听器的字符串原样保留。
     */
    private fun toggleListenerSetting(context: Context): Boolean {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, SETTING_KEY).orEmpty()
        if (current.isBlank()) return false
        val component = ComponentName(context, WeChatNotificationListener::class.java).flattenToString()
        val parts = current.split(':').filter { it.isNotBlank() }
        if (parts.none { it.equals(component, ignoreCase = true) }) return false
        val without = parts.filterNot { it.equals(component, ignoreCase = true) }.joinToString(":")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            prefs.edit().putString(KEY_BACKUP, current).commit()
            Settings.Secure.putString(resolver, SETTING_KEY, without)
            Thread.sleep(250)
            Settings.Secure.putString(resolver, SETTING_KEY, parts.joinToString(":"))
            prefs.edit().remove(KEY_BACKUP).commit()
            Diag.log(context, "guard", "已通过 secure settings 强制重绑监听")
            true
        }.getOrElse {
            Diag.log(context, "guard", "secure settings 写入失败：${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }
}
