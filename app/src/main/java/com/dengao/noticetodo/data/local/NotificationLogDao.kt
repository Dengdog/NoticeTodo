package com.dengao.noticetodo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationLogDao {

    @Query("SELECT COUNT(*) FROM notification_logs WHERE notificationKey = :key AND postTime = :postTime")
    suspend fun count(key: String, postTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationLogEntity)

    @Query("DELETE FROM notification_logs WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
