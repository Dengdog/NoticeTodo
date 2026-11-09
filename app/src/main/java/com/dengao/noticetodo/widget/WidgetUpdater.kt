package com.dengao.noticetodo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.dengao.noticetodo.NoticeTodoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件刷新入口。
 * 数据一变（增删改/完成/清空）就调用，另外交给系统每 30 分钟兜底刷新一次。
 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refreshNow(appContext) }
    }

    suspend fun refreshNow(context: Context) {
        val appContext = context.applicationContext
        val app = appContext as? NoticeTodoApp ?: return
        runCatching {
            val now = System.currentTimeMillis()
            val repository = app.container.taskRepository
            val task = repository.nextActiveTask(now)
            val stats = repository.todayStats(now)
            val manager = AppWidgetManager.getInstance(appContext)
            TaskWidgetProvider.render(appContext, manager, task, now)
            TodayWidgetProvider.render(appContext, manager, stats)
        }
    }

    fun ids(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))
}
