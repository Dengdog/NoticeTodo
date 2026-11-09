package com.dengao.noticetodo.notification

import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.parser.ParsedNotice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 进程内的“待确认草稿”通道。悬浮窗点“编辑后添加”、或兜底通知被点击时，
 * 把解析结果交给 MainActivity 打开确认页，避免用 Intent 传大段文本。
 */
object PendingDraft {

    private val _flow = MutableSharedFlow<ParsedNotice>(replay = 1, extraBufferCapacity = 4)
    val flow: SharedFlow<ParsedNotice> = _flow.asSharedFlow()

    private var pending: ParsedNotice? = null

    fun set(parsed: ParsedNotice) {
        pending = parsed
        _flow.tryEmit(parsed)
    }

    fun consume(): ParsedNotice? {
        val value = pending
        pending = null
        return value
    }

    fun clear() {
        pending = null
    }

    fun draftOf(raw: String, source: String): ParsedNotice =
        ParsedNotice(
            title = raw.lineSequence().firstOrNull()?.trim()?.take(40)?.ifBlank { "待办" } ?: "待办",
            ddl = null,
            courseTag = null,
            priority = Priority.MEDIUM,
            rawText = raw
        )
}
