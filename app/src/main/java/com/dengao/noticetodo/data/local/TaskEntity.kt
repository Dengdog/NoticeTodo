package com.dengao.noticetodo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("ddl"), Index("notificationKey"), Index("isDone")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val startTime: Long? = null,
    val ddl: Long? = null,
    val courseTag: String? = null,
    /** 0 低 / 1 中 / 2 高 */
    val priority: Int = 1,
    /** WECHAT / CLIPBOARD / SHARE / MANUAL */
    val source: String = "MANUAL",
    val rawText: String? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val notificationKey: String? = null
)
