package com.deskclock.standby

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deskclock.standby.clock.ClockPageView
import java.time.LocalTime

/**
 * 全屏横屏待机时钟：
 * - 锁屏上显示（showWhenLocked + turnScreenOn），不解除锁屏 → 退出后自然回到锁屏
 * - 拔电 / 转回竖屏 → 自动关闭
 * - 双击退出（本次充电内不再自动弹出）
 * - 窗口级亮度调节 + 夜间低亮度 + 防烧屏像素偏移
 *
 * 健壮性：启动的每一步都有兜底，任何单点异常都只会降级显示并把错误写入诊断日志，
 * 不会出现"点了预览直接闪退"。
 */
class ClockActivity : AppCompatActivity() {

    companion object {
        /** 预览模式：不因竖屏自动退出（仅供设置页预览用） */
        const val EXTRA_PREVIEW = "preview"

        /** 最近一次 onResume 的时刻（elapsedRealtime），服务据此校验界面是否真正显示 */
        @Volatile
        var lastResumeElapsed = 0L
    }

    private var pagerView: StandbyPagerView? = null
    private var fallbackClock: ClockPageView? = null
    private val ticker = Handler(Looper.getMainLooper())
    private var monitor: StandbyStateMonitor? = null
    private var burnIn: BurnInShifter? = null
    private var doubleTap: GestureDetector? = null
    private var initialBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var lastDimCheckMinute = -1
    private var finishing = false
    private var preview = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            runCatching {
                if (pagerView != null) pagerView?.onTick() else fallbackClock?.onTick()
            }
            runCatching { checkNightDim() }
            ticker.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Prefs.init(this) }

        // 锁屏显示与点亮屏幕（API 27+；个别 ROM 可能拒绝，兜底继续）
        if (Build.VERSION.SDK_INT >= 27) {
            runCatching { setShowWhenLocked(true) }
            runCatching { setTurnScreenOn(true) }
        }
        runCatching { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

        // 强制横屏；若系统不允许（非全屏窗口等），保持当前方向继续显示
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (t: Throwable) {
            CrashLog.record("orientation", t)
        }

        // 时钟内容：三页翻页 → 单时钟页 → 极简兜底
        val content = buildClockContent()
        setContentView(content)
        runCatching { hideSystemBars() }

        runCatching { initialBrightness = window.attributes.screenBrightness }
        runCatching { applyStandbyBrightness() }

        StandbyService.clockShowing = true
        StandbyService.clockShowingSince = System.currentTimeMillis()

        runCatching {
            pagerView?.let { pv ->
                burnIn = BurnInShifter(pv.shiftableView())
                burnIn?.start()
            }
        }

        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        if (!preview) {
            runCatching {
                // 热身期：Activity 自带的检测器首次回调时传感器尚无数据（默认"未横放"），
                // 必须在 2.5 秒内忽略"离开待机"判定，否则时钟刚出现就会被自己关掉。
                val monitorStart = android.os.SystemClock.elapsedRealtime()
                monitor = StandbyStateMonitor(this) { charging, placed ->
                    if (finishing) return@StandbyStateMonitor
                    val warmedUp = android.os.SystemClock.elapsedRealtime() - monitorStart > 2500
                    if (!charging && Prefs.exitOnUnplug) {
                        CrashLog.event("clock-exit", "检测到拔电，关闭时钟")
                        exitStandby(false)
                        return@StandbyStateMonitor
                    }
                    if (!placed && warmedUp && Prefs.exitOnPortrait) {
                        CrashLog.event("clock-exit", "检测到离开待机放置，关闭时钟")
                        exitStandby(false)
                    }
                }
                monitor?.start()
            }.onFailure { CrashLog.record("monitor", it) }
        }

        // 双击退出（在 Activity 层捕获触摸，避免被子视图吞掉）
        runCatching {
            doubleTap = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    exitStandby(true)
                    return true
                }
            })
        }

        ticker.post(tickRunnable)
    }

    /** 逐级降级的时钟内容：翻页容器 → 单时钟页 → 纯文本。 */
    private fun buildClockContent(): View {
        try {
            return StandbyPagerView(this).also { pagerView = it }
        } catch (t: Throwable) {
            CrashLog.record("pager", t)
        }
        try {
            return ClockPageView(this).also { fallbackClock = it }
        } catch (t: Throwable) {
            CrashLog.record("clockpage", t)
        }
        val fallback = FrameLayout(this)
        val tv = TextView(this)
        tv.text = "时钟加载异常，请到「诊断信息」查看详情"
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        tv.gravity = Gravity.CENTER
        fallback.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return fallback
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        runCatching { doubleTap?.onTouchEvent(ev) }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        lastResumeElapsed = android.os.SystemClock.elapsedRealtime()
        StandbyService.clockShowing = true
    }

    override fun onPause() {
        super.onPause()
        // 界面离开前台（锁屏/切到别的应用/被系统收起）→ 服务可重新评估是否弹出
        StandbyService.clockShowing = false
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 返回键/手势退出走统一出口，保证 clockShowing 标志复位
        exitStandby(false)
    }

    private fun exitStandby(manual: Boolean) {
        if (finishing) return
        finishing = true
        if (manual && !preview) {
            // 双击退出：本次充电内不再自动弹出，拔电后复位（预览模式不抑制）
            StandbyService.suppressUntilUnplug = true
        }
        StandbyService.clockShowing = false
        runCatching { finish() }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** 应用待机亮度（窗口级，无需系统权限） */
    private fun applyStandbyBrightness() {
        val pct = Prefs.standbyBrightnessPercent
        if (pct < 0) return
        setWindowBrightness(pct)
    }

    private fun setWindowBrightness(percent: Int) {
        val lp = window.attributes
        lp.screenBrightness = (percent / 100f).coerceIn(0.02f, 1f)
        window.attributes = lp
    }

    /** 夜间低亮度：分钟级检查，进入/离开夜间窗口时切换亮度 */
    private fun checkNightDim() {
        val now = LocalTime.now()
        val minute = now.hour * 60 + now.minute
        if (minute == lastDimCheckMinute) return
        lastDimCheckMinute = minute

        val inNight = Prefs.nightDimEnabled && isNightMinute(minute)
        if (inNight) {
            setWindowBrightness(Prefs.nightBrightnessPercent.coerceIn(2, 30))
        } else {
            if (Prefs.standbyBrightnessPercent >= 0) {
                setWindowBrightness(Prefs.standbyBrightnessPercent)
            } else {
                restoreBrightness()
            }
        }
    }

    private fun isNightMinute(minute: Int): Boolean {
        val s = Prefs.nightStartMinute
        val e = Prefs.nightEndMinute
        return if (s <= e) minute in s..e else minute >= s || minute <= e
    }

    private fun restoreBrightness() {
        val lp = window.attributes
        lp.screenBrightness = initialBrightness
        window.attributes = lp
    }

    override fun onDestroy() {
        ticker.removeCallbacksAndMessages(null)
        runCatching { monitor?.stop() }
        monitor = null
        runCatching { burnIn?.stop() }
        burnIn = null
        runCatching { restoreBrightness() }
        StandbyService.clockShowing = false
        super.onDestroy()
    }
}
