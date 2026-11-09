package com.dengao.noticetodo.data

import android.content.Context
import com.dengao.noticetodo.data.local.AppDatabase
import com.dengao.noticetodo.data.local.toDomain
import com.dengao.noticetodo.data.local.toEntity
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.notification.ReminderScheduler
import com.dengao.noticetodo.util.TimeUtils
import com.dengao.noticetodo.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val context: Context,
    database: AppDatabase
) {
    private val taskDao = database.taskDao()

    val activeTasks: Flow<List<Task>> = taskDao.observeActive().map { list -> list.map { it.toDomain() } }

    val completedTasks: Flow<List<Task>> = taskDao.observeCompleted().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Task? = taskDao.findById(id)?.toDomain()

    suspend fun insert(task: Task): Long {
        val id = taskDao.insert(task.toEntity())
        ReminderScheduler.scheduleFor(context, task.copy(id = id))
        WidgetUpdater.refresh(context)
        return id
    }

    suspend fun update(task: Task) {
        taskDao.update(task.toEntity())
        if (task.isDone || task.ddl == null) {
            ReminderScheduler.cancel(context, task.id)
        } else {
            ReminderScheduler.scheduleFor(context, task)
        }
        WidgetUpdater.refresh(context)
    }

    suspend fun setDone(task: Task, done: Boolean) {
        val now = System.currentTimeMillis()
        taskDao.setDone(task.id, done, if (done) now else null)
        if (done) ReminderScheduler.cancel(context, task.id)
        else ReminderScheduler.scheduleFor(context, task)
        WidgetUpdater.refresh(context)
    }

    suspend fun delete(task: Task) {
        taskDao.delete(task.toEntity())
        ReminderScheduler.cancel(context, task.id)
        WidgetUpdater.refresh(context)
    }

    suspend fun clearAll() {
        taskDao.allTasks().forEach { ReminderScheduler.cancel(context, it.id) }
        taskDao.clearAll()
        WidgetUpdater.refresh(context)
    }

    suspend fun todayStats(now: Long = System.currentTimeMillis()): TodayStats {
        val start = TimeUtils.startOfDay(now)
        val end = TimeUtils.startOfNextDay(now)
        return TodayStats(
            dueToday = taskDao.countDueBetween(start, end),
            doneToday = taskDao.countCompletedBetween(start, end),
            overdue = taskDao.countOverdue(now)
        )
    }

    suspend fun activeTasksOnce(): List<Task> = taskDao.activeTasks().map { it.toDomain() }

    /** 小组件用：优先最近一个未来 DDL，没有就退回到排序最靠前的未完成任务。 */
    suspend fun nextActiveTask(now: Long = System.currentTimeMillis()): Task? =
        (taskDao.nextUpcoming(now) ?: taskDao.nextActive())?.toDomain()
}

data class TodayStats(val dueToday: Int, val doneToday: Int, val overdue: Int) {
    /** 今日完成比例：已完成 / (已完成 + 今天到期未完成)，两者都为 0 时视为 0。 */
    val ratio: Float
        get() {
            val total = doneToday + dueToday
            return if (total <= 0) 0f else doneToday.toFloat() / total.toFloat()
        }
}
