package com.dengao.noticetodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dengao.noticetodo.MainActivity
import com.dengao.noticetodo.R
import com.dengao.noticetodo.data.TodayStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 2×2 小组件：今日待办数 + 完成率。
 */
class TodayWidgetProvider : AppWidgetProvider() {

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

        fun render(context: Context, manager: AppWidgetManager, stats: TodayStats) {
            val ids = WidgetUpdater.ids(context, TodayWidgetProvider::class.java)
            if (ids.isEmpty()) return
            val views = buildViews(context, stats)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, stats: TodayStats): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                7002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_today_root, pending)

            views.setTextViewText(R.id.widget_today_count, stats.dueToday.toString())
            val percent = (stats.ratio * 100).toInt()
            views.setTextViewText(R.id.widget_today_percent, "$percent%")
            views.setTextViewText(
                R.id.widget_today_sub,
                if (stats.dueToday == 0 && stats.doneToday == 0) {
                    "今天没有到期任务"
                } else {
                    "已完成 ${stats.doneToday} 个" + if (stats.overdue > 0) " · 逾期 ${stats.overdue}" else ""
                }
            )
            views.setProgressBar(R.id.widget_today_progress, 100, percent, false)
            return views
        }
    }
}
