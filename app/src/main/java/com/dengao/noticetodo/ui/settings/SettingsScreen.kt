package com.dengao.noticetodo.ui.settings

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.BuildConfig
import com.dengao.noticetodo.data.KeywordRule
import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.notification.NotificationHelper
import com.dengao.noticetodo.notification.OverlayController
import com.dengao.noticetodo.notification.PendingDraft
import com.dengao.noticetodo.parser.ParsedNotice
import com.dengao.noticetodo.ui.Draft
import com.dengao.noticetodo.ui.IncomingState
import com.dengao.noticetodo.ui.PermissionUtils
import com.dengao.noticetodo.ui.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    container: AppContainer,
    incoming: IncomingState
) {
    val viewModel: SettingsViewModel = viewModel()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val summaryEnabled by viewModel.summaryEnabled.collectAsStateWithLifecycle()
    val summaryMinutes by viewModel.summaryMinutes.collectAsStateWithLifecycle()
    val keepAlive by viewModel.keepAlive.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }

    val hasListener = remember(permissionTick) { PermissionUtils.hasNotificationAccess(context) }
    val hasOverlay = remember(permissionTick) { PermissionUtils.hasOverlay(context) }
    val hasPost = remember(permissionTick) { PermissionUtils.hasPostNotifications(context) }
    val hasExact = remember(permissionTick) { PermissionUtils.hasExactAlarm(context) }
    val hasBatteryWhitelist = remember(permissionTick) { PermissionUtils.isIgnoringBatteryOptimizations(context) }
    val listenerConnected = remember(permissionTick) {
        com.dengao.noticetodo.notification.ListenerGuard.isConnected()
    }
    val canDeepRepair = remember(permissionTick) { PermissionUtils.canDeepRepairListener(context) }

    var showAddKeyword by remember { mutableStateOf(false) }
    var newKeyword by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("权限状态")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PermissionRow(
                        name = "通知使用权",
                        granted = hasListener,
                        description = if (listenerConnected) "已连接，正在监听微信通知" else "已授权但未连接，点「重连」",
                        onOpen = { PermissionUtils.openNotificationAccess(context) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        name = "悬浮窗权限",
                        granted = hasOverlay,
                        description = "命中关键词时弹出提醒窗",
                        onOpen = { PermissionUtils.openOverlaySettings(context) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        name = "通知推送权限",
                        granted = hasPost,
                        description = "DDL 提醒与每日汇总",
                        onOpen = { PermissionUtils.openAppNotificationSettings(context) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        name = "精确闹钟",
                        granted = hasExact,
                        description = "保证 3 小时提醒准时",
                        onOpen = { PermissionUtils.openExactAlarmSettings(context) }
                    )
                }
            }

            SectionTitle("后台运行（收不到悬浮窗就改这里）")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("常驻通知保活", fontWeight = FontWeight.Medium)
                            Text(
                                "保持通知监听在后台运行，避免被杀后要打开 App 才弹窗",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = keepAlive, onCheckedChange = { viewModel.setKeepAlive(it) })
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("电池优化白名单", fontWeight = FontWeight.Medium)
                            Text(
                                if (hasBatteryWhitelist) "已允许后台运行" else "未加入白名单，系统可能冻结 App",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasBatteryWhitelist) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = { PermissionUtils.requestIgnoreBatteryOptimizations(context) }) {
                            Text(if (hasBatteryWhitelist) "查看" else "去开启")
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("自启动 / 后台弹出界面", fontWeight = FontWeight.Medium)
                            Text(
                                "小米、华为、OPPO、vivo 需要在系统里手动允许",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            if (!PermissionUtils.openAutoStartSettings(context)) {
                                PermissionUtils.openAppDetails(context)
                            }
                        }) { Text("去设置") }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("重新连接通知监听", fontWeight = FontWeight.Medium)
                            Text(
                                "监听被系统断开后可手动重绑",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            viewModel.rebindListener()
                            permissionTick++
                        }) { Text("重连") }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("深度修复通知监听", fontWeight = FontWeight.Medium)
                            Text(
                                if (canDeepRepair) "已具备权限：卡住时点「修复」强制重绑"
                                else "未授权 adb 权限，README 里有一行命令（可选）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            viewModel.rebindListener()
                            permissionTick++
                        }) { Text("修复") }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("运行日志", fontWeight = FontWeight.Medium)
                            Text(
                                "看通知有没有收到、悬浮窗为什么没弹",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { navController.navigate(Routes.DIAG) }) { Text("查看") }
                    }
                }
            }

            SectionTitle("触发关键词")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    keywords.forEach { rule ->
                        KeywordRow(
                            rule = rule,
                            onToggle = { viewModel.toggleKeyword(rule, it) },
                            onRemove = { viewModel.removeKeyword(rule) }
                        )
                    }
                    TextButton(onClick = { showAddKeyword = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加关键词")
                    }
                    Text(
                        "命中任一启用的关键词，或命中日期/时间正则，才会弹悬浮窗。“时间”“地点”这类高频词默认关闭。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle("提醒")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("DDL 前 3 小时提醒", fontWeight = FontWeight.Medium)
                            Text(
                                "闹钟不会因为 App 被杀而失效",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = reminderEnabled, onCheckedChange = { viewModel.setReminderEnabled(it) })
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("每日待办汇总", fontWeight = FontWeight.Medium)
                            Text(
                                formatMinutes(summaryMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            val hour = summaryMinutes / 60
                            val minute = summaryMinutes % 60
                            TimePickerDialog(
                                context,
                                { _, h, m -> viewModel.setSummaryTime(h * 60 + m) },
                                hour,
                                minute,
                                true
                            ).show()
                        }) { Text("改时间") }
                        Switch(checked = summaryEnabled, onCheckedChange = { viewModel.setSummaryEnabled(it) })
                    }
                }
            }

            SectionTitle("其他")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("清空所有数据", color = MaterialTheme.colorScheme.error)
                    }
                    if (BuildConfig.DEBUG) {
                        HorizontalDivider()
                        TextButton(onClick = {
                            val parsed = ParsedNotice(
                                title = "高数作业提交",
                                ddl = System.currentTimeMillis() + 3 * 60 * 60 * 1000L,
                                courseTag = "高等数学",
                                priority = Priority.HIGH,
                                rawText = "【高数】@所有人 本周五 18:00 前提交作业，收到请回复"
                            )
                            OverlayController.show(
                                context = context,
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
                                                rawText = parsed.rawText
                                            )
                                        )
                                        Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onEdit = {
                                    incoming.draft = Draft(parsed, TaskSource.WECHAT)
                                    navController.navigate(Routes.edit())
                                }
                            )
                        }) { Text("调试：模拟一条微信通知（弹悬浮窗）") }
                    }
                }
            }

            Text(
                "数据只保存在本机（Room），不联网、不登录、不上传。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddKeyword) {
        AlertDialog(
            onDismissRequest = { showAddKeyword = false },
            title = { Text("添加关键词") },
            text = {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text("关键词") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addKeyword(newKeyword)
                    newKeyword = ""
                    showAddKeyword = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newKeyword = ""
                    showAddKeyword = false
                }) { Text("取消") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有数据") },
            text = { Text("会删除全部待办与提醒，操作不可恢复。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAllData {
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PermissionRow(
    name: String,
    granted: Boolean,
    description: String,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (granted) "已开启" else "未开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onOpen) { Text(if (granted) "查看" else "去开启") }
    }
}

@Composable
private fun KeywordRow(rule: KeywordRule, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rule.text, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "删除关键词", modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatMinutes(minutes: Int): String =
    String.format("每天 %02d:%02d 推送", minutes / 60, minutes % 60)
