package com.dengao.noticetodo.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private data class GuidePage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

/**
 * 首次进主页的三步引导（只弹一次，看过就写进 DataStore）。
 * 借用参考产品的做法：不写成长文档，用三张卡片讲清「怎么加、怎么看、怎么放到桌面」。
 */
@Composable
fun GuideDialog(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val pages = remember {
        listOf(
            GuidePage(
                title = "① 怎么加待办",
                description = "右下角 + 手动新建；微信收到含「截止 / DDL / 作业」这类关键词的通知时，" +
                    "会自动弹一个悬浮窗，点「添加」就直接进列表。",
                icon = Icons.Default.Add
            ),
            GuidePage(
                title = "② 首页怎么读",
                description = "圆环是今天的完成率，中间数字是今天到期数；任务卡左侧色条是优先级，" +
                    "右侧大百分比是「离 DDL 还剩多少」，逾期会整条变红。",
                icon = Icons.Default.Done
            ),
            GuidePage(
                title = "③ 放到桌面",
                description = "长按桌面 → 小组件 → 找「通知待办」，有「最近待办 4×1」和「今日待办 2×2」" +
                    "两种尺寸，不打开 App 也能看到进度。",
                icon = Icons.Default.Settings
            )
        )
    }

    val page = pages[step]
    Dialog(onDismissRequest = onDone) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(14.dp))
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pages.indices.forEach { index ->
                            Box(
                                Modifier
                                    .size(if (index == step) 18.dp else 6.dp, 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == step) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDone) { Text("跳过") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        if (step < pages.lastIndex) step++ else onDone()
                    }) {
                        Text(if (step < pages.lastIndex) "下一步" else "开始使用")
                    }
                }
            }
        }
    }
}
