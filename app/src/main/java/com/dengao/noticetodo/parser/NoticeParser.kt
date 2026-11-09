package com.dengao.noticetodo.parser

import com.dengao.noticetodo.domain.model.Priority

/** 一条通知（或剪贴板/分享文本）解析后的结果，悬浮窗与确认页都用它。 */
data class ParsedNotice(
    val title: String,
    val ddl: Long?,
    val courseTag: String?,
    val priority: Priority,
    val rawText: String
)

/** 解析器接口：本地规则实现为 [LocalRuleParser]，后续可替换为云端实现而不影响调用方。 */
interface NoticeParser {
    fun parse(title: String?, text: String, postTime: Long = System.currentTimeMillis()): ParsedNotice
}
