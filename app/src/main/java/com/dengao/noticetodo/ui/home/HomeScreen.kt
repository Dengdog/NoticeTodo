package com.dengao.noticetodo.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dengao.noticetodo.AppContainer
import com.dengao.noticetodo.data.TodayStats
import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskBucket
import com.dengao.noticetodo.domain.model.TaskBuckets
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.parser.TriggerMatcher
import com.dengao.noticetodo.ui.Draft
import com.dengao.noticetodo.ui.IncomingState
import com.dengao.noticetodo.ui.Routes
import com.dengao.noticetodo.ui.about.GuideDialog
import com.dengao.noticetodo.util.TimeUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

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
    var guideDismissed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val guideShown by container.settingsRepository.guideShown
        .collectAsStateWithLifecycle(initialValue = true)

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
            item { TodayRingCard(stats) }

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

            TaskBucket.entries.forEach { bucket ->
                val bucketTasks = grouping[bucket].orEmpty()
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

            item { YearProgressCard() }

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

    if (!guideShown && !guideDismissed) {
        GuideDialog(onDone = {
            guideDismissed = true
            scope.launch { container.settingsRepository.setGuideShown() }
        })
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

/* ---------------- 今日卡片：环形进度 ---------------- */

@Composable
private fun TodayRingCard(stats: TodayStats) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                ProgressRing(
                    progress = stats.ratio,
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    strokeWidth = 10.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.dueToday}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = onColor
                    )
                    Text(
                        text = "今日到期",
                        style = MaterialTheme.typography.labelSmall,
                        color = onColor.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("今日待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "已完成 ${stats.doneToday} 个 · 完成率 ${(stats.ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (stats.overdue > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "逾期 ${stats.overdue} 个，尽快处理",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "累计 ${stats.doneToday + stats.dueToday} 个任务",
                    style = MaterialTheme.typography.labelSmall,
                    color = onColor.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    color: Color,
    track: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/* ---------------- 年度进度：盒阵 ---------------- */

@Composable
private fun YearProgressCard() {
    val today = remember { LocalDate.now() }
    val totalDays = today.lengthOfYear()
    val passedDays = today.dayOfYear
    val ratio = passedDays.toFloat() / totalDays.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${today.year} 已经过去",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            YearGrid(passed = passedDays, total = totalDays)
            Spacer(Modifier.height(8.dp))
            Text(
                "还剩 ${totalDays - passedDays} 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YearGrid(passed: Int, total: Int) {
    val doneColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        val rows = 7
        val cols = ceil(total / rows.toFloat()).toInt()
        val gap = 2.dp.toPx()
        val cellW = (size.width - gap * (cols - 1)) / cols
        val cellH = (size.height - gap * (rows - 1)) / rows
        val side = min(cellW, cellH)
        if (side <= 0f) return@Canvas
        val radius = CornerRadius(side * 0.28f, side * 0.28f)
        for (index in 0 until total) {
            val col = index / rows
            val row = index % rows
            drawRoundRect(
                color = if (index < passed) doneColor else trackColor,
                topLeft = Offset(col * (side + gap), row * (side + gap)),
                size = Size(side, side),
                cornerRadius = radius
            )
        }
    }
}

/* ---------------- 任务卡片 ---------------- */

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
    val percent = ((progress ?: 0f) * 100).toInt()
    val overdue = task.ddl != null && task.ddl < now && !done
    val accent = when {
        done -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        overdue -> MaterialTheme.colorScheme.error
        else -> priorityColor(task.priority)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // 左侧色条：优先级 / 逾期 / 已完成
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (progress == null) "—" else "$percent%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "更多操作",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (overdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (overdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    if (progress != null) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(accent)
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- 其它小组件 ---------------- */

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

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 8.dp),
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
    }
}

private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> Color(0xFFE53935)
    Priority.MEDIUM -> Color(0xFFFB8C00)
    Priority.LOW -> Color(0xFF43A047)
}
