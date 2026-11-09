package com.dengao.noticetodo.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.dengao.noticetodo.util.Diag

/**
 * 保活前台服务。
 *
 * 为什么需要它：Android 本身不会冻结「被系统绑定的 NotificationListenerService」，
 * 但不少国产 ROM（MIUI/HyperOS、EMUI、ColorOS 等）的省电策略会按「后台应用」处理，
 * 进程一旦被冻结，onNotificationPosted 就要等进程被唤醒（比如你打开 App）才执行——
 * 表现就是「收到通知不弹窗，打开 App 才有反应」。
 *
 * 常驻一个前台服务能把进程优先级抬到 200 左右，绝大多数 ROM 不再冻结。
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        Diag.log(this, "keepalive", "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildKeepAliveNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification)
            }
            Diag.log(this, "keepalive", "foreground started")
        } catch (t: Throwable) {
            Diag.log(this, "keepalive", "startForeground failed: ${t.javaClass.simpleName}: ${t.message}")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Diag.log(this, "keepalive", "service destroyed")
        super.onDestroy()
    }

    companion object {
        const val ID = 0x1001

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                Diag.log(context, "keepalive", "start failed: ${it.javaClass.simpleName}: ${it.message}")
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        }
    }
}
