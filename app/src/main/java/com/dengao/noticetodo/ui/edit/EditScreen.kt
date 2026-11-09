package com.dengao.noticetodo.ui.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.ui.IncomingState
import com.dengao.noticetodo.util.TimeUtils
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 确认 / 编辑页：新建、编辑、以及悬浮窗“编辑后添加”共用。
 * 字段：名称（必填）、开始时间、DDL、课程/项目、优先级、来源（只读）、原始通知文本（只读可折叠）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    navController: NavController,
    taskId: Long,
    container: AppContainer,
    incoming: IncomingState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var courseTag by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM.value) }
    var sourceName by rememberSaveable { mutableStateOf(TaskSource.MANUAL.name) }
    var ddl by rememberSaveable { mutableLongStateOf(-1L) }
    var startTime by rememberSaveable { mutableLongStateOf(-1L) }
    var rawText by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableLongStateOf(taskId) }
    var createdAt by rememberSaveable { mutableLongStateOf(-1L) }
    var completedAt by rememberSaveable { mutableLongStateOf(-1L) }
    var isDone by rememberSaveable { mutableStateOf(false) }
    var notificationKey by rememberSaveable { mutableStateOf("") }
    var showRaw by rememberSaveable { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        loaded = true
        if (taskId > 0L) {
            container.taskRepository.get(taskId)?.let { task ->
                title = task.title
                courseTag = task.courseTag.orEmpty()
                priority = task.priority.value
                sourceName = task.source.name
                ddl = task.ddl ?: -1L
                startTime = task.startTime ?: -1L
                rawText = task.rawText.orEmpty()
                createdAt = task.createdAt
                completedAt = task.completedAt ?: -1L
                isDone = task.isDone
                notificationKey = task.notificationKey.orEmpty()
            }
        } else {
            incoming.draft?.let { draft ->
                title = draft.notice.title
                courseTag = draft.notice.courseTag.orEmpty()
                priority = draft.notice.priority.value
                sourceName = draft.source.name
                ddl = draft.notice.ddl ?: -1L
                rawText = draft.notice.rawText
                incoming.draft = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingId > 0L) "编辑待办" else "新建待办") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (editingId > 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = false
                },
                label = { Text("名称（必填）") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("名称不能为空") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            TimeField(
                label = "开始时间（可空）",
                value = startTime,
                onPick = { startTime = it },
                onClear = { startTime = -1L }
            )

            TimeField(
                label = "DDL（可空）",
                value = ddl,
                onPick = { picked ->
                    // 保留原有时间部分（若之前设过），否则默认 23:59
                    val hour: Int
                    val minute: Int
                    if (ddl > 0L) {
                        val old = Calendar.getInstance().apply { timeInMillis = ddl }
                        hour = old.get(Calendar.HOUR_OF_DAY)
                        minute = old.get(Calendar.MINUTE)
                    } else {
                        hour = 23
                        minute = 59
                    }
                    val out = Calendar.getInstance().apply {
                        timeInMillis = picked
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    ddl = out.timeInMillis
                },
                onClear = { ddl = -1L }
            )

            OutlinedTextField(
                value = courseTag,
                onValueChange = { courseTag = it },
                label = { Text("课程 / 项目标签（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Column {
                Text("优先级", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { item ->
                        FilterChip(
                            selected = priority == item.value,
                            onClick = { priority = item.value },
                            label = { Text(item.label) }
                        )
                    }
                }
            }

            Text(
                text = "来源：${TaskSource.of(sourceName).label}（只读）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (rawText.isNotBlank()) {
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "收起原始通知文本" else "查看原始通知文本")
                }
                if (showRaw) {
                    Text(
                        text = rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        titleError = true
                        return@Button
                    }
                    scope.launch {
                        val task = Task(
                            id = if (editingId > 0L) editingId else 0L,
                            title = trimmed,
                            startTime = startTime.takeIf { it > 0L },
                            ddl = ddl.takeIf { it > 0L },
                            courseTag = courseTag.trim().ifBlank { null },
                            priority = Priority.of(priority),
                            source = TaskSource.of(sourceName),
                            rawText = rawText.ifBlank { null },
                            isDone = isDone,
                            createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
                            completedAt = completedAt.takeIf { it > 0L },
                            notificationKey = notificationKey.ifBlank { null }
                        )
                        if (editingId > 0L) {
                            container.taskRepository.update(task)
                        } else {
                            container.taskRepository.insert(task)
                        }
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "DDL 前 3 小时会收到提醒；没有 DDL 的任务不会逾期，归入“以后”。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除待办") },
            text = { Text("确定删除「${title.ifBlank { "该待办" }}」？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.taskRepository.get(editingId)?.let { container.taskRepository.delete(it) }
                        navController.popBackStack()
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
private fun TimeField(
    label: String,
    value: Long,
    onPick: (Long) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val calendar = Calendar.getInstance().apply {
                    if (value > 0L) timeInMillis = value
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val picked = Calendar.getInstance().apply { set(year, month, day) }.timeInMillis
                        onPick(picked)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Text(if (value > 0L) TimeUtils.formatFull(value) else "选择日期")
            }
            if (value > 0L) {
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply { timeInMillis = value }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val picked = Calendar.getInstance().apply {
                                timeInMillis = value
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                            }.timeInMillis
                            onPick(picked)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                }) { Text("改时间") }
                TextButton(onClick = onClear) { Text("清除") }
            }
        }
    }
}
