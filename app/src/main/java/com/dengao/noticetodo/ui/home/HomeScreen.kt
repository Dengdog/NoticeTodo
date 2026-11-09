package com.dengao.noticetodo.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.data.TodayStats
import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskBucket
import com.dengao.noticetodo.domain.model.TaskBuckets
import com.dengao.noticetodo.parser.TriggerMatcher
import com.dengao.noticetodo.ui.Draft
import com.dengao.noticetodo.ui.IncomingState
import com.dengao.noticetodo.ui.Routes
import com.dengao.noticetodo.util.TimeUtils
import com.dengao.noticetodo.domain.model.TaskSource
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    container: AppContainer,
    incoming: IncomingState
) {
    val viewModel: HomeViewModel = viewModel()
    val activeTasks by viewModel.active.collectAsStateWithLifecycle()
    val completedTasks by viewModel.completed.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val grouping = remember(activeTasks, now) {
        activeTasks.groupBy { TaskBuckets.bucketOf(it, now) }
    }

    // 回到前台时检查剪贴板（App 在前台才允许读剪贴板）
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val text = readClipboard(context)
        if (!text.isNullOrBlank()) {
            scope.launch {
                val rules = container.settingsRepository.currentKeywords()
                if (TriggerMatcher.isTriggered(text, rules) && text != incoming.clipboardText) {
                    incoming.clipboardText = text
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知待办", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.edit()) }) {
                Icon(Icons.Default.Add, contentDescription = "新建待办")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { TodayCard(stats) }

            incoming.clipboardText?.let { text ->
                item {
                    ClipboardBanner(
                        text = text,
                        onRecognize = {
                            val parsed = container.parser.parse(null, text)
                            incoming.draft = Draft(parsed, TaskSource.CLIPBOARD)
                            incoming.clipboardText = null
                        },
                        onDismiss = { incoming.clipboardText = null }
                    )
                }
            }

            val grouped = grouping
            TaskBucket.entries.forEach { bucket ->
                val bucketTasks = grouped[bucket].orEmpty()
                if (bucketTasks.isNotEmpty()) {
                    item(key = "header-${bucket.name}") {
                        SectionHeader(
                            title = bucket.label,
                            count = bucketTasks.size,
                            danger = bucket == TaskBucket.OVERDUE
                        )
                    }
                    items(bucketTasks, key = { "task-${it.id}" }) { task ->
                        TaskCard(
                            task = task,
                            now = now,
                            onClick = { navController.navigate(Routes.edit(task.id)) },
                            onToggleDone = { viewModel.setDone(task, true) },
                            onDelete = { pendingDelete = task }
                        )
                    }
                }
            }

            if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
                item { EmptyState() }
            }

            if (completedTasks.isNotEmpty()) {
                item(key = "completed-header") {
                    CompletedHeader(
                        expanded = showCompleted,
                        count = completedTasks.size,
                        onToggle = { showCompleted = !showCompleted }
                    )
                }
                if (showCompleted) {
                    items(completedTasks, key = { "done-${it.id}" }) { task ->
                        TaskCard(
                            task = task,
                            now = now,
                            done = true,
                            onClick = { navController.navigate(Routes.edit(task.id)) },
                            onToggleDone = { viewModel.setDone(task, false) },
                            onDelete = { pendingDelete = task }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { task ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除待办") },
            text = { Text("确定删除「${task.title}」？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(task)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

private fun readClipboard(context: android.content.Context): String? = try {
    val manager = context.getSystemService(android.content.ClipboardManager::class.java)
    val clip = manager?.primaryClip
    if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
} catch (e: Exception) {
    null
}

@Composable
private fun TodayCard(stats: TodayStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("今日待办", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${stats.dueToday}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "到期 · 已完成 ${stats.doneToday} 个" +
                    if (stats.overdue > 0) " · 逾期 ${stats.overdue} 个" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { stats.ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "完成率 ${(stats.ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ClipboardBanner(text: String, onRecognize: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("检测到通知文本，是否识别？", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = text.take(120),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("忽略") }
                TextButton(onClick = onRecognize) { Text("识别") }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompletedHeader(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已完成 $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onToggle) {
            Text(if (expanded) "收起" else "展开")
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: Task,
    now: Long,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    done: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = if (done) 1f else TaskBuckets.progressOf(task, now)
    val overdue = task.ddl != null && task.ddl < now && !done
    val barColor = when {
        done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        overdue -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(priorityColor(task.priority))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = "完成",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (done) "取消完成" else "标记完成") },
                            onClick = {
                                menuExpanded = false
                                onToggleDone()
                            },
                            leadingIcon = { Icon(Icons.Default.Done, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TimeUtils.formatDdl(task.ddl),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                task.courseTag?.let { tag ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (done) "已完成" else TimeUtils.remainingText(task.ddl, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            if (progress == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(4.dp))
                Text("无 DDL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("还没有待办", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "点右下角 + 手动新建；微信收到含“截止/DDL/作业”等关键词的通知会自动弹出悬浮窗。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(Modifier.padding(horizontal = 48.dp))
    }
}

private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> Color(0xFFE53935)
    Priority.MEDIUM -> Color(0xFFFB8C00)
    Priority.LOW -> Color(0xFF43A047)
}
