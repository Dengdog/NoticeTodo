package com.dengao.noticetodo.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.notification.ReminderScheduler
import com.dengao.noticetodo.notification.SummaryScheduler
import com.dengao.noticetodo.ui.PermissionUtils
import kotlinx.coroutines.launch

/**
 * 首次启动引导：说明用途 → 通知使用权 → 悬浮窗权限 → 通知推送权限（Android 13+）。
 * 任何一项被拒都不会阻塞，可随时跳过，设置页里能重新开。
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val hasListener = remember(tick) { PermissionUtils.hasNotificationAccess(context) }
    val hasOverlay = remember(tick) { PermissionUtils.hasOverlay(context) }
    val hasPost = remember(tick) { PermissionUtils.hasPostNotifications(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { tick++ }

    fun finish() {
        scope.launch {
            container.settingsRepository.setOnboarded()
            if (container.settingsRepository.isSummaryEnabled()) {
                SummaryScheduler.schedule(context.applicationContext, container.settingsRepository.currentSummaryMinutes())
            }
            ReminderScheduler.rescheduleAll(context.applicationContext)
            onFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("通知待办", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "把微信里“截止/DDL/作业/考试”这类通知，自动变成一个带 DDL 的待办。\n" +
                "全部在本机完成：不联网、不登录、不上传。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        StepCard(step = 1, title = "通知使用权", done = hasListener) {
            Text("用来读取微信通知栏的文字（不读取聊天记录，只读通知）。")
            Row {
                Button(onClick = { PermissionUtils.openNotificationAccess(context) }) {
                    Text(if (hasListener) "已开启 · 再检查" else "去开启")
                }
            }
        }
        StepCard(step = 2, title = "悬浮窗权限", done = hasOverlay) {
            Text("命中关键词时，在通知栏之外弹一个轻量提醒窗，可一键添加。")
            Row {
                Button(onClick = { PermissionUtils.openOverlaySettings(context) }) {
                    Text(if (hasOverlay) "已开启 · 再检查" else "去开启")
                }
            }
        }
        StepCard(step = 3, title = "通知推送权限", done = hasPost) {
            Text("用于 DDL 前 3 小时提醒和每日待办汇总。")
            Row {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPost) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PermissionUtils.openAppNotificationSettings(context)
                    }
                }) {
                    Text(if (hasPost) "已开启 · 再检查" else "去开启")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { finish() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("开始使用")
        }
        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("先跳过，之后在设置里开")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepCard(
    step: Int,
    title: String,
    done: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "$step",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (done) {
                    Text(
                        "已完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            content()
        }
    }
}
