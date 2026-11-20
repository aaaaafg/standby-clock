package com.deskclock.standby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 待机监控服务（常驻）：
 * - 充电中 → 前台服务（显示通知）+ 开启重力传感器监测
 * - 充电 + 横放（去抖后）→ 启动全屏待机时钟
 * - 拔电 → 退回后台服务（无通知），等待下次充电
 *
 * 重要（前台服务契约）：凡是用 startForegroundService() 发起的每次启动，
 * 服务都必须在数秒内调用一次 startForeground()，否则系统会抛
 * ForegroundServiceDidNotStartInTimeException 并杀死应用进程。
 * 因此 onStartCommand 里每次都要先 ensureForeground()，再按需退回后台。
 */
class StandbyService : Service() {

    companion object {
        const val ACTION_STOP = "com.deskclock.standby.action.STOP"
        const val ACTION_TEST_LAUNCH = "com.deskclock.standby.action.TEST_LAUNCH"
        private const val CHANNEL_ID = "standby_monitor"
        private const val NOTIF_ID = 1

        /** 时钟界面（Activity 或悬浮窗）当前是否显示 */
        @Volatile
        var clockShowing = false

        /** 服务当前是否在运行（供诊断页显示） */
        @Volatile
        var serviceRunning = false

        /** 上次显示开始时间；用于识别残留状态（界面被系统杀掉未回调时自动恢复） */
        @Volatile
        var clockShowingSince = 0L

        /** 最近一次检测到的充电状态（诊断用） */
        @Volatile
        var lastCharging = false

        /** 最近一次检测到的待机放置状态（侧立横放或平放朝上，诊断用） */
        @Volatile
        var lastLandscape = false

        /** 最近一次检测到的放置姿态（诊断用） */
        @Volatile
        var lastPlacement: StandbyStateMonitor.Placement = StandbyStateMonitor.Placement.UNKNOWN

        /** 超过该时长仍显示则视为残留，允许重新拉起 */
        private const val STALE_MS = 5 * 60 * 1000L

        /** 用户双击退出后，本次充电内不再自动弹出；拔电后复位 */
        @Volatile
        var suppressUntilUnplug = false

        /** 打开服务；已在运行时只刷新状态 */
        fun start(context: Context) {
            val i = Intent(context, StandbyService::class.java)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                // Android 12+ 后台限制时静默失败（前台打开应用时由 App/MainActivity 重试）
            }
        }

        /** 停止服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, StandbyService::class.java))
        }

        /** 测试：走与自动检测完全相同的"点亮屏幕+启动时钟"链路（诊断页按钮用） */
        fun testLaunch(context: Context) {
            try {
                val i = Intent(context, StandbyService::class.java).setAction(ACTION_TEST_LAUNCH)
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                CrashLog.event("test", "测试启动失败：${e.message}")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var monitor: StandbyStateMonitor? = null
    private var overlay: StandbyOverlay? = null
    private var foreground = false
    private var fgBlocked = false

    /** 唯一的"退回后台"延迟任务，避免重复调度 */
    private val dropForegroundRunnable = Runnable { dropForeground() }

    /** 屏幕点亮：若充电+待机放置但时钟未显示，重新拉起（解锁再锁屏场景的关键） */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            handler.postDelayed({
                if (Prefs.enabled && !suppressUntilUnplug && !clockShowing &&
                    lastCharging && lastLandscape
                ) {
                    CrashLog.event("screen-on", "屏幕点亮且条件满足，重新弹出时钟")
                    launchClock()
                }
            }, 600)
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceRunning = true
        Prefs.init(this)
        createChannel()
        // 满足 startForegroundService 契约：必须在数秒内 startForeground
        try {
            startForeground(NOTIF_ID, buildNotification())
            foreground = true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 31) {
                fgBlocked = true
                stopSelf()
                return
            }
        }
        ensureMonitor()
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            val flags = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_EXPORTED else 0
            registerReceiver(screenOnReceiver, filter, flags)
        } catch (e: Exception) {
            CrashLog.record("screen-receiver", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.init(this)

        // 关键：每一次 startForegroundService() 调用都必须配对一次 startForeground()，
        // 无论接下来是否要停留前台。先满足契约，再决定是否退回后台。
        if (!fgBlocked) ensureForeground()

        if (intent?.action == ACTION_TEST_LAUNCH) {
            CrashLog.event("test", "触发测试弹出")
            launchClock()
            return START_STICKY
        }

        if (fgBlocked || intent?.action == ACTION_STOP || !Prefs.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 未充电时：契约已满足，稍后把通知收掉，退回无通知的后台服务
        if (!isChargingNow()) {
            handler.removeCallbacks(dropForegroundRunnable)
            handler.postDelayed(dropForegroundRunnable, 800)
        }

        ensureMonitor()
        return START_STICKY
    }

    private fun isChargingNow(): Boolean {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = registerReceiver(null, filter) ?: return false
            val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun ensureMonitor() {
        if (monitor != null) return
        monitor = StandbyStateMonitor(this) { charging, placed ->
            lastCharging = charging
            lastLandscape = placed
            lastPlacement = monitor?.currentPlacement() ?: StandbyService.lastPlacement
            CrashLog.event("state", "charging=$charging placed=$placed")
            if (charging) {
                ensureForeground()
                monitor?.setSensorEnabled(true)
                if (placed && !clockShowing && !suppressUntilUnplug) {
                    launchClock()
                } else if (!placed && Prefs.exitOnPortrait && overlayActive()) {
                    // 悬浮窗模式下由服务负责退出
                    dismissOverlay()
                }
            } else {
                // 拔电：复位标记、关闭悬浮窗、关闭传感器、退回后台服务
                monitor?.setSensorEnabled(false)
                suppressUntilUnplug = false
                dismissOverlay()
                dropForeground()
            }
        }
        monitor?.start()
    }

    private fun launchClock() {
        if (clockShowing && System.currentTimeMillis() - clockShowingSince < STALE_MS) return
        wakeScreenBriefly()
        try {
            val i = Intent(this, ClockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            clockShowing = true
            clockShowingSince = System.currentTimeMillis()
            val launchedAt = android.os.SystemClock.elapsedRealtime()
            CrashLog.event("launch", "startActivity 已发出，2.5 秒后校验")
            // 校验：如果 2.5 秒内时钟界面没有真正 onResume（被系统拦截/挡在锁屏后），
            // 尝试用悬浮窗显示（需要悬浮窗权限）
            handler.postDelayed({
                if (ClockActivity.lastResumeElapsed < launchedAt && overlay == null) {
                    CrashLog.event("launch", "时钟界面未在 2.5 秒内显示，尝试悬浮窗兜底（overlayFallback=${Prefs.overlayFallback}）")
                    if (Prefs.overlayFallback && Settings.canDrawOverlays(this)) {
                        showOverlay()
                    }
                }
            }, 2500)
        } catch (e: Exception) {
            CrashLog.record("launch-exception", e)
            // 后台启动被系统拦截（或澎湃OS缺少"后台弹出界面"权限）→ 尝试悬浮窗备用
            if (Prefs.overlayFallback && Settings.canDrawOverlays(this)) {
                showOverlay()
            }
        }
    }

    /**
     * 短暂点亮屏幕（3 秒）。息屏/AOD 状态下 Activity 的 turnScreenOn 偶发失效，
     * 先主动唤醒可显著提高澎湃OS等机型上的成功率。
     */
    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                "standby:clock_wake"
            )
            wl.acquire(3000)
        } catch (e: Exception) {
            // 个别系统限制唤醒锁，忽略
        }
    }

    private fun showOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) return
        overlay = StandbyOverlay(this)
        overlay?.show()
        clockShowing = true
        clockShowingSince = System.currentTimeMillis()
    }

    private fun dismissOverlay() {
        overlay?.dismiss()
        overlay = null
        clockShowing = false
    }

    private fun overlayActive(): Boolean = overlay != null

    private fun ensureForeground() {
        if (foreground) return
        try {
            startForeground(NOTIF_ID, buildNotification())
            foreground = true
        } catch (e: Exception) {
            // Android 12+ 后台不允许提升前台服务时继续以后台服务运行
        }
    }

    private fun dropForeground() {
        if (!foreground) return
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // ignore
        }
        foreground = false
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = getString(R.string.notif_channel_desc)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        serviceRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (e: Exception) {
            // ignore
        }
        monitor?.stop()
        monitor = null
        dismissOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
