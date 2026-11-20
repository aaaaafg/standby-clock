package com.deskclock.standby

import android.app.Application

/**
 * 应用入口：初始化设置，并在待机模式启用时自动启动监控服务。
 * 这样即使用户从未拨动过总开关（默认开启），打开应用后监控也会开始工作。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        CrashLog.install(this)
        if (Prefs.enabled) {
            try {
                StandbyService.start(this)
            } catch (e: Exception) {
                // 后台受限时静默失败；MainActivity 打开时会再次尝试
            }
        }
    }
}
