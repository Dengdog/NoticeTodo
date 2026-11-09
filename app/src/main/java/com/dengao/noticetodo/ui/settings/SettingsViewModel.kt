package com.dengao.noticetodo.ui.settings

import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dengao.noticetodo.NoticeTodoApp
import com.dengao.noticetodo.data.KeywordRule
import com.dengao.noticetodo.notification.KeepAliveService
import com.dengao.noticetodo.notification.SummaryScheduler
import com.dengao.noticetodo.notification.WeChatNotificationListener
import com.dengao.noticetodo.util.Diag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as NoticeTodoApp).container
    private val settings = container.settingsRepository
    private val context = application.applicationContext

    val keywords = settings.keywords.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val reminderEnabled = settings.reminderEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true
    )

    val summaryEnabled = settings.summaryEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true
    )

    val summaryMinutes = settings.summaryMinutesOfDay.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 8 * 60
    )

    val keepAlive = settings.keepAlive.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true
    )

    fun addKeyword(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = settings.currentKeywords()
            if (current.any { it.text.equals(trimmed, ignoreCase = true) }) return@launch
            settings.updateKeywords(current + KeywordRule(trimmed, true))
        }
    }

    fun toggleKeyword(rule: KeywordRule, enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.currentKeywords()
            settings.updateKeywords(current.map { if (it.text == rule.text) it.copy(enabled = enabled) else it })
        }
    }

    fun removeKeyword(rule: KeywordRule) {
        viewModelScope.launch {
            val current = settings.currentKeywords()
            settings.updateKeywords(current.filterNot { it.text == rule.text })
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setReminderEnabled(enabled) }
    }

    fun setSummaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setSummaryEnabled(enabled)
            if (enabled) {
                SummaryScheduler.schedule(context, settings.currentSummaryMinutes())
            } else {
                SummaryScheduler.cancel(context)
            }
        }
    }

    fun setSummaryTime(minutesOfDay: Int) {
        viewModelScope.launch {
            settings.setSummaryMinutesOfDay(minutesOfDay)
            if (settings.isSummaryEnabled()) {
                SummaryScheduler.schedule(context, minutesOfDay)
            }
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            container.taskRepository.clearAll()
            onDone()
        }
    }

    /** 开关常驻保活：立即生效，不用等下次重启。 */
    fun setKeepAlive(enabled: Boolean) {
        viewModelScope.launch {
            settings.setKeepAlive(enabled)
            if (enabled) {
                KeepAliveService.start(context)
            } else {
                KeepAliveService.stop(context)
            }
            Diag.log(context, "keepalive", if (enabled) "用户开启，已启动前台服务" else "用户关闭，已停止前台服务")
        }
    }

    /** 手动重绑通知监听（系统断开/ROM 限制后可自救）。 */
    fun rebindListener() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(
                    ComponentName(context, WeChatNotificationListener::class.java)
                )
                Diag.log(context, "listener", "手动 requestRebind")
            }
        }
    }
}
