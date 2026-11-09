package com.dengao.noticetodo.data.local

import androidx.room.Entity

/**
 * 通知技术去重表：同一通知 key + 同一 postTime 只处理一次。
 * 微信同一会话会复用 notification key，因此必须带上 postTime，否则后续消息会被全部吞掉。
 */
@Entity(tableName = "notification_logs", primaryKeys = ["notificationKey", "postTime"])
data class NotificationLogEntity(
    val notificationKey: String,
    val postTime: Long,
    val createdAt: Long = System.currentTimeMillis()
)
