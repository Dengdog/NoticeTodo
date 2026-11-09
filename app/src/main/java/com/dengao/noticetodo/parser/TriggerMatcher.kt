package com.dengao.noticetodo.parser

import com.dengao.noticetodo.data.KeywordRule

/**
 * 触发判定：命中任一“启用的关键词”，或命中日期/时间类正则，才弹出悬浮窗。
 * 关键词表可在设置页增删启停。
 */
object TriggerMatcher {

    private val simpleRegexes = listOf(
        Regex("""\d{1,2}月\d{1,2}日"""),
        Regex("""\d{4}-\d{1,2}-\d{1,2}"""),
        Regex("""\d{1,2}\s*[:：]\s*\d{2}""")
    )

    private val relativeWords = listOf("今天", "明天", "后天", "本周", "下周")
    private val timeWordRegex = Regex("""\d{1,2}\s*[:：]\s*\d{2}""")

    fun isTriggered(text: String, rules: List<KeywordRule>): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        if (rules.any { it.enabled && it.text.isNotBlank() && lower.contains(it.text.lowercase()) }) {
            return true
        }
        if (simpleRegexes.any { it.containsMatchIn(text) }) return true
        // “今天/明天/后天/本周/下周”必须同时出现明确时刻才算命中
        if (relativeWords.any { text.contains(it) } && timeWordRegex.containsMatchIn(text)) return true
        return false
    }
}
