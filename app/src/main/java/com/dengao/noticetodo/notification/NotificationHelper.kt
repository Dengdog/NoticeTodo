package com.dengao.noticetodo.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dengao.noticetodo.MainActivity
import com.dengao.noticetodo.R
import com.dengao.noticetodo.data.TodayStats
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.parser.ParsedNotice
import com.dengao.noticetodo.util.TimeUtils

object NotificationHelper {

    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_SUMMARY = "summary"
    const val CHANNEL_CATCH = "catch"
    const val CHANNEL_KEEPALIVE = "keepalive"

    private var nextId = 2000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "DDL 提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "DDL 前 3 小时提醒"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "每日待办汇总", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天固定时间推送今日待办"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CATCH, "抓到的待办通知", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "悬浮窗被错过时，点击回到确认页"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_KEEPALIVE, "后台保活", NotificationManager.IMPORTANCE_MIN).apply {
                description = "保持通知监听在后台运行，避免被系统冻结"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    /** 常驻通知：让进程保持在前台服务级别，避免后台回调被冻结。 */
    fun buildKeepAliveNotification(context: Context): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            9100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("通知待办正在监听微信通知")
            .setContentText("保活中，收到命中关键词的消息会立刻弹窗")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pending)
            .build()
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun postReminder(context: Context, task: Task) {
        if (!canPost(context)) return
        val ddl = task.ddl ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TASK_ID, task.id)
        }
        val pending = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("还有 3 小时：${task.title}")
            .setContentText("DDL ${TimeUtils.formatDdl(ddl)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("DDL ${TimeUtils.formatDdl(ddl)}\n${task.courseTag ?: ""}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        notify(context, task.id.toInt() and 0xFFFF, notification)
    }

    fun postSummary(context: Context, stats: TodayStats, overdueTitles: List<String>) {
        if (!canPost(context)) return
        val text = buildString {
            append("今日到期 ${stats.dueToday} 个，已完成 ${stats.doneToday} 个")
            if (stats.overdue > 0) append("，逾期 ${stats.overdue} 个")
            if (overdueTitles.isNotEmpty()) {
                append("\n逾期：").append(overdueTitles.take(3).joinToString("、"))
            }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("今日待办汇总")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        notify(context, 9001, notification)
    }

    /** 悬浮窗错过时的兜底：点通知回到确认页。 */
    fun postCaughtNotice(context: Context, parsed: ParsedNotice) {
        if (!canPost(context)) return
        PendingDraft.set(parsed)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DRAFT, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            parsed.rawText.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (parsed.ddl == null) {
            "未识别到 DDL，点击手动补全"
        } else {
            "DDL ${TimeUtils.formatDdl(parsed.ddl)}"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(parsed.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\n${parsed.rawText.take(120)}"))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        notify(context, nextId++, notification)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // 通知权限被撤销时静默失败，不影响其他功能
        }
    }
}
