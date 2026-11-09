package com.dengao.noticetodo.ui.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 确认 / 编辑页。改成参考产品那种「大标题 + 表单行」的结构：
 * 标题单独一行大字，其余字段是「图标 + 名称 + 当前值 + 箭头」的行，点开才选。
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
    var showCourseDialog by remember { mutableStateOf(false) }
    var courseInput by remember { mutableStateOf("") }
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
                .padding(horizontal = 20.dp)
        ) {
            // 大标题输入
            BasicTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = false
                },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                decorationBox = { inner ->
                    if (title.isBlank()) {
                        Text(
                            "要做什么？",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                    inner()
                }
            )
            if (titleError) {
                Text(
                    "名称不能为空",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            FormRow(
                icon = Icons.Default.Event,
                label = "DDL",
                value = if (ddl > 0L) TimeUtils.formatFull(ddl) else "未设置",
                highlight = ddl > 0L
            ) {
                pickDateTime(context, ddl, defaultHour = 23, defaultMinute = 59) { ddl = it }
            }
            if (ddl > 0L) {
                Row(Modifier.padding(start = 44.dp)) {
                    TextButton(onClick = {
                        pickTimeOnly(context, ddl) { ddl = it }
                    }) { Text("只改时间") }
                    TextButton(onClick = { ddl = -1L }) { Text("清除") }
                }
            }

            FormRow(
                icon = Icons.Default.Schedule,
                label = "开始时间",
                value = if (startTime > 0L) TimeUtils.formatFull(startTime) else "未设置",
                highlight = startTime > 0L
            ) {
                pickDateTime(context, startTime, defaultHour = 9, defaultMinute = 0) { startTime = it }
            }
            if (startTime > 0L) {
                Row(Modifier.padding(start = 44.dp)) {
                    TextButton(onClick = { startTime = -1L }) { Text("清除") }
                }
            }

            FormRow(
                icon = Icons.Default.School,
                label = "课程 / 项目",
                value = courseTag.ifBlank { "未设置" },
                highlight = courseTag.isNotBlank()
            ) {
                courseInput = courseTag
                showCourseDialog = true
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(14.dp))
                Text("优先级", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.padding(start = 36.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Priority.entries.forEach { item ->
                    FilterChip(
                        selected = priority == item.value,
                        onClick = { priority = item.value },
                        label = { Text(item.label) }
                    )
                }
            }

            SourceRow(label = TaskSource.of(sourceName).label)

            if (rawText.isNotBlank()) {
                FormRow(
                    icon = Icons.Default.Notes,
                    label = "原始通知文本",
                    value = if (showRaw) "点此收起" else "点此查看",
                    highlight = false
                ) { showRaw = !showRaw }
                if (showRaw) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, top = 4.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = rawText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
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
                    .height(52.dp)
                    .padding(bottom = 0.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (editingId > 0L) "保存" else "完成创建", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "DDL 前 3 小时会收到提醒；没有 DDL 的任务不会逾期，归入「以后」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCourseDialog) {
        AlertDialog(
            onDismissRequest = { showCourseDialog = false },
            title = { Text("课程 / 项目") },
            text = {
                OutlinedTextField(
                    value = courseInput,
                    onValueChange = { courseInput = it },
                    label = { Text("标签名，留空表示不设置") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    courseTag = courseInput.trim()
                    showCourseDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCourseDialog = false }) { Text("取消") }
            }
        )
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
private fun FormRow(
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SourceRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(14.dp))
        Text("来源", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "$label（只读）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 先选日期，再选时间，一次设完。 */
private fun pickDateTime(
    context: Context,
    initial: Long,
    defaultHour: Int,
    defaultMinute: Int,
    onPicked: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        if (initial > 0L) {
            timeInMillis = initial
        } else {
            set(Calendar.HOUR_OF_DAY, defaultHour)
            set(Calendar.MINUTE, defaultMinute)
        }
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val out = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(out.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun pickTimeOnly(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val out = Calendar.getInstance().apply {
                timeInMillis = initial
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            onPicked(out.timeInMillis)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    ).show()
}
