package com.dengao.noticetodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.dengao.noticetodo.MainActivity
import com.dengao.noticetodo.R
import com.dengao.noticetodo.domain.model.Priority
import com.dengao.noticetodo.domain.model.Task
import com.dengao.noticetodo.domain.model.TaskBuckets
import com.dengao.noticetodo.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 4×1 小组件：最近一个待办 + 百分比 + 细进度条。
 * 只显示一条，信息极简，扫一眼就知道「下一个要交的是什么、还剩多少」。
 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.refreshNow(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        fun render(
            context: Context,
            manager: AppWidgetManager,
            task: Task?,
            now: Long
        ) {
            val ids = WidgetUpdater.ids(context, TaskWidgetProvider::class.java)
            if (ids.isEmpty()) return
            val views = buildViews(context, task, now)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, task: Task?, now: Long): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_task)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                task?.let { putExtra(MainActivity.EXTRA_OPEN_TASK_ID, it.id) }
            }
            val pending = PendingIntent.getActivity(
                context,
                7001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            if (task == null) {
                views.setViewVisibility(R.id.widget_content, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                return views
            }

            views.setViewVisibility(R.id.widget_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)

            views.setTextViewText(R.id.widget_title, task.title)
            views.setTextViewText(
                R.id.widget_deadline,
                if (task.ddl == null) "无 DDL" else "DDL " + TimeUtils.formatDdl(task.ddl)
            )

            val progress = TaskBuckets.progressOf(task, now)
            val percent = ((progress ?: 0f) * 100).toInt()
            val overdue = task.ddl != null && task.ddl < now

            views.setTextViewText(R.id.widget_percent, if (progress == null) "—" else "$percent%")
            views.setTextViewText(
                R.id.widget_remaining,
                if (task.ddl == null) "不急" else TimeUtils.remainingText(task.ddl, now)
            )
            views.setProgressBar(R.id.widget_progress, 100, percent, false)

            // 进度条 + 百分比跟随状态变色
            val accent = when {
                overdue -> Color.parseColor("#FFE53935")
                else -> priorityColor(context, task.priority)
            }
            views.setTextColor(R.id.widget_percent, accent)
            views.setInt(R.id.widget_strip, "setBackgroundColor", priorityColor(context, task.priority))
            return views
        }

        private fun priorityColor(context: Context, priority: Priority): Int = when (priority) {
            Priority.HIGH -> Color.parseColor("#FFE53935")
            Priority.MEDIUM -> Color.parseColor("#FFFB8C00")
            Priority.LOW -> Color.parseColor("#FF43A047")
        }
    }
}
