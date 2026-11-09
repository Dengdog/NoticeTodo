package com.dengao.noticetodo.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import com.dengao.noticetodo.MainActivity
import com.dengao.noticetodo.NoticeTodoApp
import com.dengao.noticetodo.data.local.NotificationLogEntity
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.parser.TriggerMatcher
import com.dengao.noticetodo.util.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 微信通知监听。
 *
 * 去重键是 (notificationKey, postTime)：微信同一会话会复用 key，只用 key 去重会把后续消息全部吞掉。
 * 只有命中触发关键词/日期正则才弹悬浮窗，其余通知直接忽略。
 * 连上监听时顺带拉起前台服务保活，否则部分 ROM 会冻结进程，导致回调要等 App 打开才执行。
 */
class WeChatNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Diag.log(this, "listener", "connected")
        ListenerGuard.onConnected()
        keepAliveIfEnabled()
    }

    override fun onListenerDisconnected() {
        Diag.log(this, "listener", "disconnected, requestRebind")
        ListenerGuard.onDisconnected()
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, WeChatNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName != WECHAT_PACKAGE) return
        val extras = notification.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = if (bigText.length > shortText.length) bigText else shortText
        if (body.isBlank()) return

        val key = notification.key ?: return
        val postTime = notification.postTime
        Diag.log(this, "notify", "收到微信通知 key=${key.takeLast(24)} body=${body.take(36)}")

        scope.launch {
            val appContext = applicationContext
            val container = (appContext as? NoticeTodoApp)?.container ?: run {
                Diag.log(appContext, "notify", "container 为空，忽略")
                return@launch
            }
            val logDao = container.database.notificationLogDao()
            if (logDao.count(key, postTime) > 0) {
                Diag.log(appContext, "notify", "重复通知（同 key+postTime），跳过")
                return@launch
            }
            logDao.insert(NotificationLogEntity(notificationKey = key, postTime = postTime))

            val rules = container.settingsRepository.currentKeywords()
            val haystack = "$title\n$body"
            if (!TriggerMatcher.isTriggered(haystack, rules)) {
                Diag.log(appContext, "notify", "未命中关键词，忽略")
                return@launch
            }

            val parsed = container.parser.parse(title, body, postTime)
            Diag.log(
                appContext, "parse",
                "命中 title=${parsed.title} ddl=${parsed.ddl?.let { java.util.Date(it) }}"
            )

            withContext(Dispatchers.Main) {
                val shown = OverlayController.show(
                    context = appContext,
                    parsed = parsed,
                    onAdd = {
                        scope.launch {
                            container.taskRepository.insert(
                                Task(
                                    title = parsed.title,
                                    ddl = parsed.ddl,
                                    courseTag = parsed.courseTag,
                                    priority = parsed.priority,
                                    source = TaskSource.WECHAT,
                                    rawText = parsed.rawText,
                                    notificationKey = key
                                )
                            )
                            Diag.log(appContext, "overlay", "已添加待办：${parsed.title}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(appContext, "已添加到待办", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onEdit = {
                        PendingDraft.set(parsed)
                        startActivity(
                            Intent(appContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra(MainActivity.EXTRA_OPEN_DRAFT, true)
                            }
                        )
                    }
                )
                if (!shown) {
                    Diag.log(appContext, "overlay", "悬浮窗没能显示，已退化为通知")
                }
            }
        }
    }

    private fun keepAliveIfEnabled() {
        val appContext = applicationContext
        scope.launch {
            val app = appContext as? NoticeTodoApp ?: return@launch
            if (app.container.settingsRepository.isKeepAlive()) {
                KeepAliveService.start(appContext)
            } else {
                Diag.log(appContext, "keepalive", "用户关闭了保活，跳过前台服务")
            }
        }
    }

    override fun onDestroy() {
        Diag.log(this, "listener", "destroyed")
        ListenerGuard.onDisconnected()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
    }
}
