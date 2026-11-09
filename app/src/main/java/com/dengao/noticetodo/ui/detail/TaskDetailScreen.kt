package com.dengao.noticetodo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskBuckets
import com.dengao.noticetodo.domain.model.TaskStatus
import com.dengao.noticetodo.ui.Routes
import com.dengao.noticetodo.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * 任务详情：只读视图 + 操作入口。
 * 从主页卡片点进来；要改内容点右上角「编辑」进编辑页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: Long,
    container: AppContainer
) {
    val scope = rememberCoroutineScope()
    val task by container.taskRepository.observeTask(taskId)
        .collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val current = task
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { navController.navigate(Routes.edit(taskId)) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (current != null) {
                DetailActions(
                    task = current,
                    onToggleDone = {
                        scope.launch { container.taskRepository.setDone(current, !current.isDone) }
                    },
                    onToggleCancelled = {
                        scope.launch { container.taskRepository.setCancelled(current, !current.cancelled) }
                    },
                    onDelete = { confirmDelete = true }
                )
            }
        }
    ) { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("任务不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val now = System.currentTimeMillis()
        val progress = TaskBuckets.progressOf(current, now)
        val percent = ((progress ?: 0f) * 100).toInt()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            StatusChip(current, now)
            Spacer(Modifier.height(10.dp))
            Text(
                text = current.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            // 进度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (progress == null) "无 DDL" else "$percent%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (current.isOverdue(now)) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (current.cancelled) "已终止"
                    else if (current.isDone) "已完成"
                    else TimeUtils.remainingText(current.ddl, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                if (progress != null) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(
                                when {
                                    current.cancelled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    current.isOverdue(now) -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()

            DetailRow("DDL", if (current.ddl == null) "未设置" else TimeUtils.formatFull(current.ddl!!))
            DetailRow("开始时间", current.startTime?.let { TimeUtils.formatFull(it) } ?: "未设置")
            DetailRow("课程 / 项目", current.courseTag ?: "未设置")
            DetailRow("优先级", current.priority.label)
            DetailRow("来源", current.source.label)
            DetailRow("创建时间", TimeUtils.formatFull(current.createdAt))
            current.completedAt?.let { DetailRow("完成时间", TimeUtils.formatFull(it)) }

            if (!current.rawText.isNullOrBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (showRaw) "收起原始文本" else "查看原始文本")
                }
                if (showRaw) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = current.rawText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除待办") },
            text = { Text("确定删除「${current?.title ?: ""}」？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    current?.let { item ->
                        scope.launch {
                            container.taskRepository.delete(item)
                            navController.popBackStack()
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StatusChip(task: Task, now: Long) {
    val (label, color) = when {
        task.cancelled -> "已终止" to MaterialTheme.colorScheme.onSurfaceVariant
        task.isDone -> "已完成" to MaterialTheme.colorScheme.primary
        task.isOverdue(now) -> "已逾期 ${TimeUtils.overdueDays(task.ddl!!, now)} 天" to MaterialTheme.colorScheme.error
        else -> "进行中" to MaterialTheme.colorScheme.primary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailActions(
    task: Task,
    onToggleDone: () -> Unit,
    onToggleCancelled: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onToggleDone, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (task.isDone) "取消完成" else "完成")
            }
            OutlinedButton(onClick = onToggleCancelled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (task.cancelled) "恢复" else "终止")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
