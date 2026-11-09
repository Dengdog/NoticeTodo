package com.dengao.noticetodo.domain.model

/** 优先级：0 低 / 1 中 / 2 高，数据库存 Int，默认中。 */
enum class Priority(val value: Int, val label: String) {
    LOW(0, "低"),
    MEDIUM(1, "中"),
    HIGH(2, "高");

    companion object {
        fun of(value: Int): Priority = entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}

/** 任务来源，只读展示。 */
enum class TaskSource(val label: String) {
    WECHAT("微信"),
    CLIPBOARD("剪贴板"),
    SHARE("分享"),
    MANUAL("手动");

    companion object {
        fun of(name: String?): TaskSource =
            entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

data class Task(
    val id: Long = 0L,
    val title: String,
    val startTime: Long? = null,
    val ddl: Long? = null,
    val courseTag: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val source: TaskSource = TaskSource.MANUAL,
    val rawText: String? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val notificationKey: String? = null
)
