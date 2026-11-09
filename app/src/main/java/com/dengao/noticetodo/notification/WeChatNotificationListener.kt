package com.dengao.noticetodo.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import com.dengao.noticetodo.MainActivity
import com.dengao.noticetodo.NoticeTodoApp
import com.dengao.noticetodo.data.local.NotificationLogEntity
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.parser.TriggerMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 微信通知监听。
 * 去重键是 (notificationKey, postTime)：微信同一会话会复用 key，只用 key 去重会把后续消息全部吞掉。
 * 只有命中触发关键词/日期正则才弹悬浮窗，其余通知直接忽略。
 */
class WeChatNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        scope.launch {
            val appContext = applicationContext
            val container = (appContext as? NoticeTodoApp)?.container ?: return@launch
            val logDao = container.database.notificationLogDao()
            if (logDao.count(key, postTime) > 0) return@launch
            logDao.insert(NotificationLogEntity(notificationKey = key, postTime = postTime))

            val rules = container.settingsRepository.currentKeywords()
            val haystack = "$title\n$body"
            if (!TriggerMatcher.isTriggered(haystack, rules)) return@launch

            val parsed = container.parser.parse(title, body, postTime)
            withContext(Dispatchers.Main) {
                OverlayController.show(
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
            }
        }
    }

    /** 监听服务被系统解绑后尝试重新连接，避免“装完就再也不弹”。 */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, WeChatNotificationListener::class.java))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
    }
}
