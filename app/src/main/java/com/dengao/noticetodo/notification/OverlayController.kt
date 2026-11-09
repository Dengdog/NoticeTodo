package com.dengao.noticetodo.notification

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.dengao.noticetodo.parser.ParsedNotice
import com.dengao.noticetodo.util.Diag
import com.dengao.noticetodo.util.TimeUtils

/**
 * 轻量悬浮窗（SYSTEM_ALERT_WINDOW + WindowManager）。
 * 三个按钮：添加 / 编辑后添加 / 忽略。可拖动，15 秒无操作自动收起。
 * 没有悬浮窗权限或添加失败时，退化为一条可点击的兜底通知。
 */
object OverlayController {

    private const val AUTO_DISMISS_MS = 15_000L

    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    fun show(
        context: Context,
        parsed: ParsedNotice,
        onAdd: () -> Unit,
        onEdit: () -> Unit
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            Diag.log(context, "overlay", "没有悬浮窗权限，退化为通知")
            NotificationHelper.postCaughtNotice(context, parsed)
            return false
        }
        dismiss()
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        val density = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16, density), dp(14, density), dp(16, density), dp(10, density))
            background = GradientDrawable().apply {
                cornerRadius = dp(18, density).toFloat()
                setColor(0xF2202124.toInt())
                setStroke(dp(1, density), 0x33FFFFFF)
            }
        }

        val titleView = TextView(context).apply {
            text = parsed.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val ddlView = TextView(context).apply {
            text = if (parsed.ddl == null) {
                "未识别到 DDL，可编辑后添加"
            } else {
                "DDL  ${TimeUtils.formatDdl(parsed.ddl)}"
            }
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4, density), 0, dp(8, density))
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val addButton = smallButton(context, "添加", 0xFF4C5BD4.toInt()) {
            dismiss()
            onAdd()
        }
        val editButton = smallButton(context, "编辑后添加", 0x33FFFFFF) {
            dismiss()
            onEdit()
        }
        val ignoreButton = smallButton(context, "忽略", 0x22FFFFFF) {
            dismiss()
        }
        buttonRow.addView(addButton)
        buttonRow.addView(editButton)
        buttonRow.addView(ignoreButton)

        container.addView(titleView)
        container.addView(ddlView)
        container.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            dp(312, density),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(84, density)
        }

        // 拖动：按住标题区域移动整个窗口，并重置自动收起计时
        titleView.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var originX = 0
            private var originY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        originX = params.x
                        originY = params.y
                        resetTimer()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = originX + (event.rawX - startX).toInt()
                        params.y = originY + (event.rawY - startY).toInt()
                        runCatching { windowManager.updateViewLayout(container, params) }
                        resetTimer()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        resetTimer()
                        return true
                    }
                }
                return false
            }
        })

        // 点按钮也算“有操作”，点击时已经 dismiss，这里只需要保持计时器重置
        container.setOnClickListener { resetTimer() }

        try {
            windowManager.addView(container, params)
            rootView = container
            layoutParams = params
            resetTimer()
            Diag.log(context, "overlay", "悬浮窗已显示")
            return true
        } catch (e: Exception) {
            Diag.log(context, "overlay", "addView 失败：${e.javaClass.simpleName}: ${e.message}")
            NotificationHelper.postCaughtNotice(context, parsed)
            return false
        }
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val view = rootView ?: return
        rootView = null
        layoutParams = null
        runCatching {
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        }
    }

    private fun resetTimer() {
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
    }

    private fun smallButton(context: Context, label: String, color: Int, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(10, context.resources.displayMetrics.density).toFloat()
                setColor(color)
            }
            setPadding(dp(10, context.resources.displayMetrics.density), 0, dp(10, context.resources.displayMetrics.density), 0)
            minHeight = 0
            minimumHeight = 0
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(38, context.resources.displayMetrics.density)
            )
            lp.marginStart = dp(8, context.resources.displayMetrics.density)
            layoutParams = lp
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
