package com.dengao.noticetodo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "noticetodo_settings")

data class KeywordRule(val text: String, val enabled: Boolean)

class SettingsRepository(private val context: Context) {

    private val store = context.appDataStore

    /** 触发关键词。默认只开高置信词，“时间/地点”这类高频噪声词默认关闭，可在设置页手动打开。 */
    val keywords: Flow<List<KeywordRule>> = store.data.map { prefs ->
        decodeKeywords(prefs[KEY_KEYWORDS])
    }

    val reminderEnabled: Flow<Boolean> = store.data.map { it[KEY_REMINDER] ?: true }

    val summaryEnabled: Flow<Boolean> = store.data.map { it[KEY_SUMMARY] ?: true }

    val summaryMinutesOfDay: Flow<Int> = store.data.map { it[KEY_SUMMARY_TIME] ?: DEFAULT_SUMMARY_MINUTES }

    val onboarded: Flow<Boolean> = store.data.map { it[KEY_ONBOARDED] ?: false }

    suspend fun updateKeywords(rules: List<KeywordRule>) {
        store.edit { it[KEY_KEYWORDS] = encodeKeywords(rules) }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[KEY_REMINDER] = enabled }
    }

    suspend fun setSummaryEnabled(enabled: Boolean) {
        store.edit { it[KEY_SUMMARY] = enabled }
    }

    suspend fun setSummaryMinutesOfDay(minutes: Int) {
        store.edit { it[KEY_SUMMARY_TIME] = minutes }
    }

    suspend fun setOnboarded() {
        store.edit { it[KEY_ONBOARDED] = true }
    }

    suspend fun currentKeywords(): List<KeywordRule> = keywords.first()

    suspend fun currentSummaryMinutes(): Int = summaryMinutesOfDay.first()

    suspend fun isSummaryEnabled(): Boolean = summaryEnabled.first()

    suspend fun isReminderEnabled(): Boolean = reminderEnabled.first()

    companion object {
        const val DEFAULT_SUMMARY_MINUTES = 8 * 60

        private val KEY_KEYWORDS = stringSetPreferencesKey("keywords")
        private val KEY_REMINDER = booleanPreferencesKey("reminder_enabled")
        private val KEY_SUMMARY = booleanPreferencesKey("summary_enabled")
        private val KEY_SUMMARY_TIME = intPreferencesKey("summary_minutes")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")

        /** 默认关键词：第二项为是否默认开启。 */
        private val DEFAULT_KEYWORDS: List<Pair<String, Boolean>> = listOf(
            "截止" to true,
            "DDL" to true,
            "ddl" to true,
            "提交" to true,
            "作业" to true,
            "报告" to true,
            "论文" to true,
            "考试" to true,
            "签到" to true,
            "报名" to true,
            "会议" to true,
            "活动" to true,
            "请于" to true,
            "务必" to true,
            "之前完成" to true,
            "时间" to false,
            "地点" to false
        )

        fun defaultKeywords(): List<KeywordRule> =
            DEFAULT_KEYWORDS.map { KeywordRule(it.first, it.second) }

        private fun encodeKeywords(rules: List<KeywordRule>): Set<String> =
            rules.map { "${if (it.enabled) "1" else "0"}:${it.text}" }.toSet()

        private fun decodeKeywords(raw: Set<String>?): List<KeywordRule> {
            if (raw.isNullOrEmpty()) return defaultKeywords()
            return raw.mapNotNull { item ->
                val idx = item.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val enabled = item.substring(0, idx) == "1"
                val text = item.substring(idx + 1)
                if (text.isBlank()) null else KeywordRule(text, enabled)
            }.sortedBy { it.text }
        }
    }
}
