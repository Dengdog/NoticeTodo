package com.dengao.noticetodo

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dengao.noticetodo.domain.model.TaskSource
import com.dengao.noticetodo.notification.OverlayController
import com.dengao.noticetodo.notification.PendingDraft
import com.dengao.noticetodo.parser.TriggerMatcher
import com.dengao.noticetodo.ui.AppNavHost
import com.dengao.noticetodo.ui.Draft
import com.dengao.noticetodo.ui.IncomingState
import com.dengao.noticetodo.ui.theme.NoticeTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val incoming = IncomingState()
    private var lastClipboardText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NoticeTodoApp).container
        handleIntent(intent, container)

        setContent {
            val viewModel: AppViewModel = viewModel()
            val onboarded by viewModel.onboarded.collectAsStateWithLifecycle()
            NoticeTodoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val flag = onboarded
                    if (flag == null) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        AppNavHost(container = container, onboarded = flag, incoming = incoming)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, (application as NoticeTodoApp).container)
    }

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    private fun handleIntent(intent: Intent?, container: AppContainer) {
        if (intent == null) return

        if (intent.getBooleanExtra(EXTRA_OPEN_DRAFT, false)) {
            PendingDraft.consume()?.let { incoming.draft = Draft(it, TaskSource.WECHAT) }
        }

        val taskId = intent.getLongExtra(EXTRA_OPEN_TASK_ID, -1L)
        if (taskId > 0L) incoming.openTaskId = taskId

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (shared.isNotBlank()) {
                val parsed = container.parser.parse(null, shared)
                incoming.draft = Draft(parsed, TaskSource.SHARE)
            }
        }
    }

    private fun checkClipboard() {
        val container = (application as NoticeTodoApp).container
        val text = readClipboard() ?: return
        if (text == lastClipboardText) return
        lastClipboardText = text
        lifecycleScope.launch {
            val rules = container.settingsRepository.currentKeywords()
            if (TriggerMatcher.isTriggered(text, rules)) {
                incoming.clipboardText = text
            }
        }
    }

    private fun readClipboard(): String? = try {
        val manager = getSystemService(ClipboardManager::class.java)
        val clip = manager?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    override fun onDestroy() {
        OverlayController.dismiss()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_DRAFT = "open_draft"
        const val EXTRA_OPEN_TASK_ID = "open_task_id"
    }
}
