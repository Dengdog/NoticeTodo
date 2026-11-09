package com.dengao.noticetodo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("ddl"), Index("notificationKey"), Index("isDone"), Index("cancelled")]
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
    /** 终止：主动放弃这个任务，不算完成、也不算逾期。v2 新增。 */
    @ColumnInfo(defaultValue = "0")
    val cancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val notificationKey: String? = null
)
