package com.deskclock.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 待机状态监视器：
 * - 充电状态：监听 ACTION_BATTERY_CHANGED
 * - 放置姿态：重力传感器（缺失时回退加速度计）
 *
 * "待机放置"判定（满足其一即可）：
 * 1) 侧立横放：X 轴重力分量明显大于 Y（手机横着立在充电座上）
 * 2) 平放朝上：Z 轴重力分量 ≈ +9.8（手机平放在桌面、屏幕朝上充电）
 *
 * "离开待机"判定：
 * - 竖放（Y 轴占优，用户把手机拿起来）
 * - 平放朝下（屏幕朝下）
 */
class StandbyStateMonitor(
    context: Context,
    private val onState: (charging: Boolean, standbyPlaced: Boolean) -> Unit
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    @Volatile private var charging = false
    @Volatile private var placed = false
    @Volatile private var placement = Placement.UNKNOWN

    // 平滑后的重力向量
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f

    // 去抖状态
    private var desired = false
    private var desiredPending = false
    private var lastEmitCharging: Boolean? = null
    private var lastEmitPlaced: Boolean? = null

    /** 当前放置姿态分类（供诊断页显示/判定） */
    enum class Placement {
        SIDE_LANDSCAPE, // 侧立横放
        FLAT_UP,        // 平放朝上
        PORTRAIT,       // 竖放
        FLAT_DOWN,      // 平放朝下
        UNKNOWN         // 斜放/过渡
    }

    private val applyRunnable = Runnable {
        desiredPending = false
        if (placed != desired) {
            placed = desired
            emitIfChanged()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val now = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (now != charging) {
                charging = now
                emitIfChanged()
            }
        }
    }

    private var sensor: Sensor? = null
    private var registered = false
    private var sensorEnabled = false

    fun start() {
        if (registered) return
        registered = true

        // 电池状态（sticky）
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val flags = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_EXPORTED else 0
        appContext.registerReceiver(batteryReceiver, filter, flags)

        // 初始充电状态
        try {
            val sticky = appContext.registerReceiver(null, filter)
            if (sticky != null) {
                val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            // 个别系统限制 sticky 读取时忽略，后续电池广播会更新状态
        }

        desired = placed
        handler.post { emitIfChanged() }
    }

    /**
     * 开/关重力传感器。未充电时应关闭以省电；
     * 重新开启时重置放置状态，避免使用过期值。
     */
    fun setSensorEnabled(enabled: Boolean) {
        if (enabled == sensorEnabled) return
        sensorEnabled = enabled
        if (enabled) {
            registerSensor()
            placed = false
            desired = false
            desiredPending = false
            placement = Placement.UNKNOWN
            handler.removeCallbacks(applyRunnable)
            emitIfChanged()
        } else {
            unregisterSensor()
            placed = false
            desired = false
            desiredPending = false
            placement = Placement.UNKNOWN
            handler.removeCallbacks(applyRunnable)
            emitIfChanged()
        }
    }

    private fun registerSensor() {
        if (sensorManager == null || sensor != null) return
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
    }

    private fun unregisterSensor() {
        sensorManager?.unregisterListener(this)
        sensor = null
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            appContext.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // 未注册时忽略
        }
        unregisterSensor()
        sensorEnabled = false
        handler.removeCallbacks(applyRunnable)
    }

    fun currentCharging(): Boolean = charging
    fun currentPlaced(): Boolean = placed
    fun currentPlacement(): Placement = placement

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            gx = event.values[0]
            gy = event.values[1]
            gz = event.values[2]
        } else {
            val alpha = 0.85f
            gx = alpha * gx + (1 - alpha) * event.values[0]
            gy = alpha * gy + (1 - alpha) * event.values[1]
            gz = alpha * gz + (1 - alpha) * event.values[2]
        }

        val ax = Math.abs(gx)
        val ay = Math.abs(gy)

        // 分类放置姿态（阈值单位 m/s²，重力模长约 9.8）
        val p = when {
            // 侧立横放：X 轴分量占优（手机横着立起来，屏幕朝向侧面）
            ax > 3.2f && ax > ay -> Placement.SIDE_LANDSCAPE
            // 平放朝上：Z 轴明显为正
            gz > 7.5f -> Placement.FLAT_UP
            // 竖放：Y 轴占优（拿起手机）
            ay > 3.2f && ay > ax + 1.0f -> Placement.PORTRAIT
            // 平放朝下
            gz < -7.5f -> Placement.FLAT_DOWN
            else -> Placement.UNKNOWN
        }
        if (p != placement) placement = p

        val next = when (p) {
            Placement.SIDE_LANDSCAPE, Placement.FLAT_UP -> true   // 待机放置
            Placement.PORTRAIT, Placement.FLAT_DOWN -> false     // 离开待机
            Placement.UNKNOWN -> placed                          // 过渡状态保持
        }

        if (next != desired) {
            desired = next
            if (!desiredPending) {
                desiredPending = true
                handler.removeCallbacks(applyRunnable)
                handler.postDelayed(applyRunnable, 1500)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // 无需处理
    }

    private fun emitIfChanged() {
        if (charging != lastEmitCharging || placed != lastEmitPlaced) {
            lastEmitCharging = charging
            lastEmitPlaced = placed
            onState(charging, placed)
        }
    }
}
