package com.dengao.noticetodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.parser.ParsedNotice

/** 一条待确认草稿：解析结果 + 来源。 */
data class Draft(val notice: ParsedNotice, val source: TaskSource)

/**
 * 跨页面的待处理事件：分享/悬浮窗/剪贴板进来的文本、以及“打开某条任务”。
 * 用 Compose State 承载，MainActivity 写入、导航层消费。
 */
class IncomingState {
    var draft: Draft? by mutableStateOf(null)
    var openTaskId: Long? by mutableStateOf(null)
    var clipboardText: String? by mutableStateOf(null)
}
