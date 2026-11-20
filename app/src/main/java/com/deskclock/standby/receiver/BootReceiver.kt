package com.deskclock.standby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deskclock.standby.Prefs
import com.deskclock.standby.StandbyService

/** 开机自启（需在澎湃OS开启"自启动"权限）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(context)
        if (Prefs.enabled && Prefs.bootStart) {
            try {
                StandbyService.start(context)
            } catch (e: Exception) {
                // 后台启动受限时静默失败，用户下次打开应用时自动恢复
            }
        }
    }
}
