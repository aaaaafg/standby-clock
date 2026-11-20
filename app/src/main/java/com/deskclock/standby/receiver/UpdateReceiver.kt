package com.deskclock.standby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deskclock.standby.Prefs
import com.deskclock.standby.StandbyService

/**
 * 应用更新（覆盖安装）后自动重启监控服务。
 * ACTION_MY_PACKAGE_REPLACED 属于隐式广播豁免名单，manifest 注册有效。
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Prefs.init(context)
        if (Prefs.enabled) {
            try {
                StandbyService.start(context)
            } catch (e: Exception) {
                // 后台启动受限时静默失败，用户下次打开应用时自动恢复
            }
        }
    }
}
