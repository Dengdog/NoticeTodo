package com.dengao.noticetodo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query(
        "SELECT * FROM tasks WHERE isDone = 0 AND cancelled = 0 " +
            "ORDER BY (ddl IS NULL) ASC, ddl ASC, createdAt DESC"
    )
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND cancelled = 1 ORDER BY createdAt DESC")
    fun observeCancelled(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND cancelled = 0")
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

    @Query("UPDATE tasks SET isDone = :done, completedAt = :completedAt, cancelled = 0 WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    /** 终止任务：不算完成，单独归到「已终止」，不再参与逾期/提醒。 */
    @Query("UPDATE tasks SET cancelled = :cancelled, isDone = 0, completedAt = NULL WHERE id = :id")
    suspend fun setCancelled(id: Long, cancelled: Boolean)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Query(
        "SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND cancelled = 0 " +
            "AND ddl IS NOT NULL AND ddl >= :from AND ddl < :to"
    )
    suspend fun countDueBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 1 AND completedAt IS NOT NULL AND completedAt >= :from AND completedAt < :to")
    suspend fun countCompletedBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND cancelled = 0 AND ddl IS NOT NULL AND ddl < :now")
    suspend fun countOverdue(now: Long): Int

    /** 小组件用：最近一个未来 DDL 的未完成任务。 */
    @Query(
        "SELECT * FROM tasks WHERE isDone = 0 AND cancelled = 0 AND ddl IS NOT NULL AND ddl > :now " +
            "ORDER BY ddl ASC LIMIT 1"
    )
    suspend fun nextUpcoming(now: Long): TaskEntity?

    /** 小组件兜底：没有任何未来 DDL 时，取排序最靠前的未完成任务。 */
    @Query(
        "SELECT * FROM tasks WHERE isDone = 0 AND cancelled = 0 " +
            "ORDER BY (ddl IS NULL) ASC, ddl ASC, createdAt DESC LIMIT 1"
    )
    suspend fun nextActive(): TaskEntity?
}
