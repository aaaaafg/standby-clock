package com.deskclock.standby

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置存储。
 * 使用前必须先调用 [init]（App 入口、Service、Receiver 里各自保证）。
 */
object Prefs {
    private const val FILE = "standby_prefs"
    private lateinit var sp: SharedPreferences
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            initialized = true
        }
    }

    // ---------- 基础开关 ----------

    /** 总开关：启用待机模式 */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(value) { sp.edit().putBoolean("enabled", value).apply() }

    /** 开机自启动 */
    var bootStart: Boolean
        get() = sp.getBoolean("boot_start", false)
        set(value) { sp.edit().putBoolean("boot_start", value).apply() }

    /** 悬浮窗备用模式（Activity 无法启动时用悬浮窗显示时钟） */
    var overlayFallback: Boolean
        get() = sp.getBoolean("overlay_fallback", false)
        set(value) { sp.edit().putBoolean("overlay_fallback", value).apply() }

    /** 拔充电线自动退出 */
    var exitOnUnplug: Boolean
        get() = sp.getBoolean("exit_on_unplug", true)
        set(value) { sp.edit().putBoolean("exit_on_unplug", value).apply() }

    /** 转回竖屏自动退出 */
    var exitOnPortrait: Boolean
        get() = sp.getBoolean("exit_on_portrait", true)
        set(value) { sp.edit().putBoolean("exit_on_portrait", value).apply() }

    // ---------- 外观 ----------

    /** 字体颜色（默认白色） */
    var fontColor: Int
        get() = sp.getInt("font_color", 0xFFFFFFFF.toInt())
        set(value) { sp.edit().putInt("font_color", value).apply() }

    /** 显示秒 */
    var showSeconds: Boolean
        get() = sp.getBoolean("show_seconds", true)
        set(value) { sp.edit().putBoolean("show_seconds", value).apply() }

    /** 时钟数字大小（百分比，60-180，默认 130 = 超大） */
    var clockScalePercent: Int
        get() = sp.getInt("clock_scale", 130)
        set(value) { sp.edit().putInt("clock_scale", value.coerceIn(60, 180)).apply() }

    /** 0 = 跟随系统；1 = 强制 24 小时制；2 = 强制 12 小时制 */
    var hourMode: Int
        get() = sp.getInt("hour_mode", 0)
        set(value) { sp.edit().putInt("hour_mode", value).apply() }

    /** -1 = 跟随系统亮度；0-100 = 待机期间窗口亮度百分比 */
    var standbyBrightnessPercent: Int
        get() = sp.getInt("standby_brightness", -1)
        set(value) { sp.edit().putInt("standby_brightness", value).apply() }

    // ---------- 防烧屏 ----------

    /** 像素缓慢偏移 */
    var antiBurnIn: Boolean
        get() = sp.getBoolean("anti_burnin", true)
        set(value) { sp.edit().putBoolean("anti_burnin", value).apply() }

    /** 夜间低亮度模式 */
    var nightDimEnabled: Boolean
        get() = sp.getBoolean("night_dim", true)
        set(value) { sp.edit().putBoolean("night_dim", value).apply() }

    /** 夜间开始时刻（分钟数，默认 22:00） */
    var nightStartMinute: Int
        get() = sp.getInt("night_start", 22 * 60)
        set(value) { sp.edit().putInt("night_start", value).apply() }

    /** 夜间结束时刻（分钟数，默认 07:00） */
    var nightEndMinute: Int
        get() = sp.getInt("night_end", 7 * 60)
        set(value) { sp.edit().putInt("night_end", value).apply() }

    /** 夜间窗口亮度百分比（默认 8%） */
    var nightBrightnessPercent: Int
        get() = sp.getInt("night_brightness", 8)
        set(value) { sp.edit().putInt("night_brightness", value).apply() }

    // ---------- 天气 ----------

    /** 优先使用定位（否则用手动城市） */
    var useLocation: Boolean
        get() = sp.getBoolean("use_location", true)
        set(value) { sp.edit().putBoolean("use_location", value).apply() }

    /** 手动设置的城市名 */
    var cityName: String?
        get() = sp.getString("city_name", null)
        set(value) { sp.edit().putString("city_name", value).apply() }

    /** 城市纬度（默认北京） */
    var cityLat: Float
        get() = sp.getFloat("city_lat", 39.9042f)
        set(value) { sp.edit().putFloat("city_lat", value).apply() }

    /** 城市经度（默认北京） */
    var cityLon: Float
        get() = sp.getFloat("city_lon", 116.4074f)
        set(value) { sp.edit().putFloat("city_lon", value).apply() }

    fun setCity(name: String?, lat: Double, lon: Double) {
        sp.edit()
            .putString("city_name", name)
            .putFloat("city_lat", lat.toFloat())
            .putFloat("city_lon", lon.toFloat())
            .apply()
    }

    /** 最近一次天气原始 JSON 缓存 */
    var lastWeatherJson: String?
        get() = sp.getString("last_weather_json", null)
        set(value) { sp.edit().putString("last_weather_json", value).apply() }

    /** 最近一次天气更新时间（毫秒） */
    var lastWeatherTime: Long
        get() = sp.getLong("last_weather_time", 0L)
        set(value) { sp.edit().putLong("last_weather_time", value).apply() }
}
