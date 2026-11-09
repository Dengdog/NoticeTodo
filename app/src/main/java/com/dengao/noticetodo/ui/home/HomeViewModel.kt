package com.dengao.noticetodo.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dengao.noticetodo.NoticeTodoApp
import com.dengao.noticetodo.data.TodayStats
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as NoticeTodoApp).container.taskRepository

    val active = repository.activeTasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val completed = repository.completedTasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val cancelled = repository.cancelledTasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** 今日卡片直接从列表派生，任务一变化立刻更新，不依赖轮询。 */
    val stats: StateFlow<TodayStats> = combine(active, completed, now) { activeTasks, doneTasks, current ->
        val start = TimeUtils.startOfDay(current)
        val end = TimeUtils.startOfNextDay(current)
        TodayStats(
            dueToday = activeTasks.count { it.ddl != null && it.ddl >= start && it.ddl < end },
            doneToday = doneTasks.count { it.completedAt != null && it.completedAt >= start && it.completedAt < end },
            overdue = activeTasks.count { it.ddl != null && it.ddl < current }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayStats(0, 0, 0))

    init {
        // 时间推进：进度条与“剩余 X 小时”每 20 秒刷新一次
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                delay(20_000L)
            }
        }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { repository.setDone(task, done) }
    }

    fun setCancelled(task: Task, cancelled: Boolean) {
        viewModelScope.launch { repository.setCancelled(task, cancelled) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { repository.delete(task) }
    }
}
