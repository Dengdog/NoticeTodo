package com.dengao.noticetodo.parser

import com.dengao.noticetodo.domain.model.Priority

/**
 * 本地规则解析：名称取正文首行或「」内文字，DDL 交给 [DdlExtractor]，
 * 课程/项目取《》或【】内文字，优先级按关键词粗判。全部失败就留空，不猜测。
 */
class LocalRuleParser : NoticeParser {

    private val bracketNameRegex = Regex("""[「『](.*?)[」』]""")
    private val courseRegex = Regex("""[《【](.*?)[》】]""")
    private val noiseWords = listOf("@所有人", "收到请回复", "收到回复", "请回复", "[图片]", "[表情]", "点击查看")

    override fun parse(title: String?, text: String, postTime: Long): ParsedNotice {
        val raw = text.replace("\r\n", "\n").trim()
        val name = extractName(raw) ?: title?.trim()?.takeIf { it.isNotBlank() } ?: "微信通知"
        val ddl = DdlExtractor.extract(raw, postTime)
        val course = courseRegex.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return ParsedNotice(
            title = name.take(40),
            ddl = ddl,
            courseTag = course?.take(20),
            priority = extractPriority(raw),
            rawText = raw
        )
    }

    private fun extractName(text: String): String? {
        bracketNameRegex.find(text)?.groupValues?.get(1)?.let { inner ->
            if (inner.isNotBlank()) return clean(inner).take(40).takeIf { it.isNotBlank() }
        }
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val cleaned = clean(firstLine)
        // 去掉开头的【课程】/《课程》前缀，避免标题里重复出现课程名
        val withoutPrefix = cleaned.replace(Regex("""^[【\[《].{1,12}?[】\]》]\s*"""), "")
        val finalName = withoutPrefix.ifBlank { cleaned }
        return finalName.take(40).takeIf { it.isNotBlank() }
    }

    private fun clean(value: String): String {
        var s = value
        noiseWords.forEach { s = s.replace(it, "") }
        s = s.replace(Regex("""\s+"""), " ")
        // 去掉首尾的冒号、破折号等标点
        return s.trim().trim('：', ':', '-', '—', '，', ',', '。', '；', ';')
    }

    private fun extractPriority(text: String): Priority = when {
        listOf("紧急", "务必", "重要", "今天").any { text.contains(it) } -> Priority.HIGH
        listOf("尽快", "本周").any { text.contains(it) } -> Priority.MEDIUM
        else -> Priority.MEDIUM
    }
}
