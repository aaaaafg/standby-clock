package com.deskclock.standby

import android.content.Context
import android.os.BatteryManager
import com.deskclock.standby.perm.PermissionChecker

/**
 * 诊断信息：监控服务、关键权限与最近崩溃日志的文本汇总，供诊断页显示。
 */
object Diagnostics {

    fun build(context: Context): String {
        val sb = StringBuilder()
        sb.append("应用版本：1.4.0\n")
        sb.append("设备：${RomInfo.displayName}\n")
        sb.append("待机模式：${if (Prefs.enabled) "已启用" else "未启用"}\n")
        sb.append("监控服务：${if (StandbyService.serviceRunning) "运行中" else "未运行"}\n")
        sb.append("时钟界面：${if (StandbyService.clockShowing) "显示中" else "未显示"}\n")
        sb.append("当前充电：${if (isCharging(context)) "是" else "否"}\n")
        sb.append("最近检测：充电=${if (StandbyService.lastCharging) "是" else "否"} · 放置=${placementText(StandbyService.lastPlacement)}\n\n")

        sb.append("—— 关键权限 ——\n")
        sb.append("悬浮窗：${ok(PermissionChecker.overlay(context))}\n")
        sb.append("电池不优化：${ok(PermissionChecker.batteryOptimization(context))}\n")
        sb.append("通知：${ok(PermissionChecker.notifications(context))}\n")
        sb.append("日历：${ok(PermissionChecker.calendar(context) == PermissionChecker.Status.GRANTED)}\n")
        sb.append("定位：${ok(PermissionChecker.location(context) == PermissionChecker.Status.GRANTED)}\n")
        if (RomInfo.isXiaomi) {
            sb.append("自启动：${statusText(PermissionChecker.autoStart(context))}\n")
            sb.append("后台弹出界面：${statusText(PermissionChecker.backgroundPopup(context))}\n")
        }

        sb.append("\n—— 最近异常记录 ——\n")
        sb.append(CrashLog.readLast() ?: "（无）")
        return sb.toString()
    }

    private fun ok(b: Boolean): String = if (b) "已开启" else "未开启"

    private fun placementText(p: StandbyStateMonitor.Placement): String = when (p) {
        StandbyStateMonitor.Placement.SIDE_LANDSCAPE -> "侧立横放"
        StandbyStateMonitor.Placement.FLAT_UP -> "平放朝上"
        StandbyStateMonitor.Placement.PORTRAIT -> "竖放"
        StandbyStateMonitor.Placement.FLAT_DOWN -> "平放朝下"
        StandbyStateMonitor.Placement.UNKNOWN -> "未识别"
    }

    private fun statusText(s: PermissionChecker.Status): String = when (s) {
        PermissionChecker.Status.GRANTED -> "已开启"
        PermissionChecker.Status.DENIED -> "未开启"
        PermissionChecker.Status.UNKNOWN -> "无法检测（请人工确认）"
        PermissionChecker.Status.NOT_NEEDED -> "不适用"
    }

    private fun isCharging(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.isCharging == true
        } catch (e: Exception) {
            false
        }
    }
}
