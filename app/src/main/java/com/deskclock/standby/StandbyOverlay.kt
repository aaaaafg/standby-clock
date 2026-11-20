package com.deskclock.standby

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * 悬浮窗备用模式：当系统禁止从后台启动 Activity（如澎湃OS缺少"后台弹出界面"权限）时，
 * 用全屏悬浮窗显示同一套待机时钟界面（StandbyPagerView）。
 * 需要"悬浮窗"权限；双击退出（本次充电内不再自动弹出）。
 */
class StandbyOverlay(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val pagerView = StandbyPagerView(appContext)
    private val root = OverlayRoot(appContext)
    private val ticker = Handler(Looper.getMainLooper())
    private var shown = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            pagerView.onTick()
            ticker.postDelayed(this, 1000)
        }
    }

    init {
        root.addView(pagerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.onDoubleTap = {
            // 手势回调中延迟执行，避免在触摸分发过程中移除窗口
            ticker.post {
                StandbyService.suppressUntilUnplug = true
                dismiss()
            }
        }
    }

    fun show() {
        if (shown) return
        if (!Settings.canDrawOverlays(appContext)) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        params.gravity = Gravity.TOP or Gravity.START
        try {
            windowManager.addView(root, params)
            shown = true
            ticker.post(tickRunnable)
        } catch (e: Exception) {
            // 权限或系统限制，静默失败
        }
    }

    fun dismiss() {
        if (!shown) return
        ticker.removeCallbacksAndMessages(null)
        try {
            windowManager.removeView(root)
        } catch (e: Exception) {
            // ignore
        }
        shown = false
    }

    fun isShowing(): Boolean = shown

    /** 在 Activity 层捕获双击手势（子视图会消费普通触摸） */
    private class OverlayRoot(context: Context) : FrameLayout(context) {
        var onDoubleTap: (() -> Unit)? = null

        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleTap?.invoke()
                return true
            }
        })

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            detector.onTouchEvent(ev)
            return super.dispatchTouchEvent(ev)
        }
    }
}
