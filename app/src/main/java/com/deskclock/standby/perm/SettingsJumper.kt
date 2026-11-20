package com.deskclock.standby.perm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 跳转系统设置页：优先澎湃OS/MIUI 专用页面，失败回退通用页面。
 * 每个方法返回是否成功跳转。
 */
object SettingsJumper {

    /** 悬浮窗 */
    fun openOverlay(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        openAppDetails(context)
    }

    /** 电池优化白名单 */
    fun openBatteryOptimization(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            return openAppDetails(context)
        }
        return try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e2: Exception) {
                openAppDetails(context)
            }
        }
    }

    /** 通知设置 */
    fun openNotification(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        openAppDetails(context)
    }

    /** 修改系统设置（可选） */
    fun openWriteSettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        openAppDetails(context)
    }

    /** 小米自启动管理页 */
    fun openAutoStart(context: Context): Boolean = tryComponents(
        context,
        listOf(
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    )

    /** 小米"后台弹出界面"权限页 */
    fun openBackgroundPopup(context: Context): Boolean = tryComponents(
        context,
        listOf(
            "com.miui.securitycenter/com.miui.permcenter.permissions.AppPermissionsEditorActivity",
            "com.miui.securitycenter/com.miui.permcenter.permissions.PermissionsEditorActivity"
        )
    )

    /** 应用详情页（兜底：所有设置都能从这里进入） */
    fun openAppDetails(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    private fun tryComponents(context: Context, components: List<String>): Boolean {
        for (c in components) {
            val parts = c.split("/")
            if (parts.size != 2) continue
            val intent = Intent()
            intent.component = ComponentName(parts[0], parts[1])
            intent.putExtra("extra_pkg", context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // 尝试下一个
            }
        }
        return openAppDetails(context)
    }
}
