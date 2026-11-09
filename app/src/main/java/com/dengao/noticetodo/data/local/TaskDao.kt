package com.dengao.noticetodo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY (ddl IS NULL) ASC, ddl ASC, createdAt DESC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isDone = 0")
    suspend fun activeTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<TaskEntity>

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Delete
    suspend fun delete(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET isDone = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND ddl IS NOT NULL AND ddl >= :from AND ddl < :to")
    suspend fun countDueBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 1 AND completedAt IS NOT NULL AND completedAt >= :from AND completedAt < :to")
    suspend fun countCompletedBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND ddl IS NOT NULL AND ddl < :now")
    suspend fun countOverdue(now: Long): Int
}
