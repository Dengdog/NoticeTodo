package com.dengao.noticetodo

import android.content.Context
import com.dengao.noticetodo.data.SettingsRepository
import com.dengao.noticetodo.data.TaskRepository
import com.dengao.noticetodo.data.local.AppDatabase
import com.dengao.noticetodo.parser.LocalRuleParser
import com.dengao.noticetodo.parser.NoticeParser

/** 极简手写依赖容器：一个 Application 级别的单例，够用且不用引入 DI 框架。 */
class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.get(context)

    val taskRepository: TaskRepository = TaskRepository(context, database)

    val settingsRepository: SettingsRepository = SettingsRepository(context)

    val parser: NoticeParser = LocalRuleParser()
}
