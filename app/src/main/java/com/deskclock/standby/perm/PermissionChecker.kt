package com.deskclock.standby.perm

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deskclock.standby.RomInfo

/**
 * 权限状态自动检测。返回状态供状态面板显示。
 */
object PermissionChecker {

    enum class Status { GRANTED, DENIED, UNKNOWN, NOT_NEEDED }

    /** 悬浮窗 */
    fun overlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 电池优化白名单（电池不优化） */
    fun batteryOptimization(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }

    /** 通知 */
    fun notifications(context: Context): Boolean {
        return try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (e: Exception) {
            true
        }
    }

    /** 日历（读取日程） */
    fun calendar(context: Context): Status =
        runtimePermission(context, Manifest.permission.READ_CALENDAR)

    /** 定位（天气） */
    fun location(context: Context): Status {
        val coarse = runtimePermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = runtimePermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return if (coarse == Status.GRANTED || fine == Status.GRANTED) Status.GRANTED else coarse
    }

    /** 修改系统亮度（可选） */
    fun writeSettings(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (e: Exception) {
            false
        }
    }

    /** 澎湃OS"后台弹出界面"（基于 background_start_activity appop，仅小米系有意义） */
    fun backgroundPopup(context: Context): Status =
        if (RomInfo.isXiaomi) {
            checkAppOpString(context, "android:background_start_activity")
        } else {
            Status.NOT_NEEDED
        }

    /** 小米自启动（appop 10008，非公开接口，尽力检测，仅供参考） */
    fun autoStart(context: Context): Status =
        if (RomInfo.isXiaomi) {
            checkAppOpInt(context, 10008)
        } else {
            Status.NOT_NEEDED
        }

    private fun runtimePermission(context: Context, perm: String): Status =
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            Status.GRANTED
        } else {
            Status.DENIED
        }

    private fun checkAppOpString(context: Context, op: String): Status {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return Status.UNKNOWN
            val mode = appOps.checkOpNoThrow(op, Process.myUid(), context.packageName)
            if (mode == AppOpsManager.MODE_ALLOWED) Status.GRANTED else Status.DENIED
        } catch (e: Exception) {
            Status.UNKNOWN
        }
    }

    /**
     * 小米自启动 appop（op=10008）的 int 重载已从新版 SDK stub 移除，
     * 通过反射调用（框架运行时始终存在该方法），失败返回 UNKNOWN。
     */
    private fun checkAppOpInt(context: Context, op: Int): Status {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return Status.UNKNOWN
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = method.invoke(appOps, op, Process.myUid(), context.packageName) as Int
            if (mode == AppOpsManager.MODE_ALLOWED) Status.GRANTED else Status.DENIED
        } catch (e: Exception) {
            Status.UNKNOWN
        }
    }
}
