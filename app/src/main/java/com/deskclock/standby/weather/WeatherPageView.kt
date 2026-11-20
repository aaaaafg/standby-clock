package com.deskclock.standby.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.deskclock.standby.Prefs
import com.deskclock.standby.R
import com.deskclock.standby.StandbyColors
import com.deskclock.standby.StandbyPage
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 天气页：iOS StandBy 风格，纵向居中。
 * 大温度 → 天气现象 → 最高/最低 → 城市名 → 未来 6 小时横条 → 更新时间。
 * 数据源 Open-Meteo；成功响应缓存到 Prefs，30 分钟内复用。
 */
class WeatherPageView(context: Context) : FrameLayout(context), StandbyPage {

    private companion object {
        const val CACHE_MAX_AGE_MS = 30 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())

    /** 是否已有刷新请求在进行中 */
    @Volatile
    private var refreshing = false

    /** 已成功地理编码的城市名，避免每次刷新重复查询（后台线程写、跨刷新读） */
    @Volatile
    private var resolvedCity: String? = null

    private val contentView: LinearLayout
    private val statusView: TextView
    private val tempView: TextView
    private val condView: TextView
    private val hiLoView: TextView
    private val cityView: TextView
    private val hourlyRow: LinearLayout
    private val updatedView: TextView

    init {
        tempView = TextView(context).apply {
            text = "–°"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 110f)
            setAutoSizeTextTypeUniformWithConfiguration(56, 110, 2, TypedValue.COMPLEX_UNIT_SP)
            setTextColor(StandbyColors.fontColor())
            typeface = Typeface.create(StandbyColors.TYPEFACE_LIGHT, Typeface.NORMAL)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        condView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(StandbyColors.SECONDARY)
            typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        hiLoView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(StandbyColors.SECONDARY)
            typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        cityView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(StandbyColors.TERTIARY)
            typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        hourlyRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        updatedView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(StandbyColors.TERTIARY)
            typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        contentView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                tempView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                condView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10f) }
            )
            addView(
                hiLoView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6f) }
            )
            addView(
                cityView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(14f) }
            )
            addView(
                hourlyRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(32f) }
            )
            addView(
                updatedView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(32f) }
            )
            visibility = View.GONE
        }
        statusView = TextView(context).apply {
            text = "天气加载中…"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(StandbyColors.TERTIARY)
            typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
            setOnClickListener { startRefresh() }
        }
        addView(contentView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** 每秒调用：天气页无逐秒更新需求，留空实现 */
    override fun onTick() {
    }

    /** 页面可见时刷新数据：无缓存或缓存超过 30 分钟则重新请求 */
    override fun onPageShown() {
        val json = Prefs.lastWeatherJson
        val now = System.currentTimeMillis()
        val stale = json.isNullOrEmpty() || now - Prefs.lastWeatherTime > CACHE_MAX_AGE_MS
        if (json != null && tryRenderCache(json)) {
            // 有可展示的数据（过期数据也先展示），过期时后台刷新
            if (stale && !refreshing) {
                startRefresh()
            }
        } else if (!refreshing) {
            startRefresh()
        } else {
            showStatus("天气加载中…")
        }
    }

    override fun view(): View = this

    private fun startRefresh() {
        if (refreshing) return
        // 无网络时直接给出明确提示，而不是一直转圈
        if (!isNetworkAvailable()) {
            showStatus("无网络连接\n请连接网络后点按重试")
            return
        }
        refreshing = true
        if (contentView.visibility != View.VISIBLE) {
            showStatus("天气加载中…")
        }
        thread(name = "weather-refresh") {
            try {
                val target = resolveLocation()
                val json = WeatherClient.fetchForecastJson(target.lat, target.lon)
                handler.post { onWeatherFetched(json, target.display, target.note) }
            } catch (e: Exception) {
                val msg = when {
                    e is java.net.UnknownHostException -> "无法解析服务器地址（网络受限）"
                    e is java.net.SocketTimeoutException -> "网络超时"
                    e is java.io.IOException -> "网络异常"
                    else -> e.message
                }
                handler.post { onWeatherFailed(msg) }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    private fun onWeatherFetched(json: String, display: String, note: String?) {
        refreshing = false
        val parsed = parseWeather(json)
        if (parsed == null) {
            onWeatherFailed("数据解析失败")
            return
        }
        Prefs.lastWeatherJson = json
        Prefs.lastWeatherTime = System.currentTimeMillis()
        render(parsed, display, Prefs.lastWeatherTime, note)
    }

    private fun onWeatherFailed(message: String? = null) {
        refreshing = false
        showStatus("天气加载失败" + (message?.let { "（$it）" } ?: "") + "\n点按重试")
    }

    /**
     * 决定请求坐标与显示的城市名：
     * 定位模式且有权限 → LocationManager 最近位置（NETWORK 优先，其次 GPS），显示"当前位置"；
     * 否则用 Prefs 手动城市坐标；手动城市名不为空且未解析过时先地理编码并回写 Prefs。
     * 本方法在后台线程执行。
     */
    private fun resolveLocation(): LocationTarget {
        if (Prefs.useLocation) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    // 1) 最近一次已知位置
                    try {
                        val loc: Location? = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        if (loc != null) {
                            return LocationTarget(loc.latitude, loc.longitude, "当前位置", null)
                        }
                    } catch (e: SecurityException) {
                        // 权限在检查后被收回，回退到手动城市
                    }
                    // 2) 主动请求一次当前位置（API 30+，最多等 4 秒）
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        try {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            val result = arrayOfNulls<Location>(1)
                            lm.getCurrentLocation(
                                LocationManager.NETWORK_PROVIDER,
                                null,
                                context.mainExecutor
                            ) { loc ->
                                result[0] = loc
                                latch.countDown()
                            }
                            latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
                            result[0]?.let {
                                return LocationTarget(it.latitude, it.longitude, "当前位置", null)
                            }
                        } catch (e: Exception) {
                            // 获取失败，继续用手动城市
                        }
                    }
                }
                // 定位成功但没拿到坐标：用默认/手动城市，并提示
                val manual1 = Prefs.cityName
                val lat1 = Prefs.cityLat.toDouble()
                val lon1 = Prefs.cityLon.toDouble()
                val name1 = manual1?.takeIf { it.isNotEmpty() } ?: "北京"
                return LocationTarget(lat1, lon1, name1, "定位暂不可用，已用$name1")
            }
        }
        val manualCity = Prefs.cityName
        var lat = Prefs.cityLat.toDouble()
        var lon = Prefs.cityLon.toDouble()
        var display = manualCity?.takeIf { it.isNotEmpty() } ?: "北京"
        val note = when {
            !Prefs.useLocation && manualCity.isNullOrEmpty() -> "未设置城市，默认北京"
            !Prefs.useLocation -> null
            else -> "定位未授权，已用$display"
        }
        if (display != "北京" && display != resolvedCity) {
            try {
                val result = WeatherClient.searchCity(display)
                if (result != null) {
                    Prefs.setCity(result.name, result.latitude, result.longitude)
                    lat = result.latitude
                    lon = result.longitude
                    display = result.name
                    resolvedCity = display
                }
            } catch (e: Exception) {
                // 地理编码失败：沿用已有坐标，下次刷新重试
            }
        }
        return LocationTarget(lat, lon, display, note)
    }

    /** 缓存渲染时推断显示名（成功地理编码后的名字已回写 Prefs.cityName） */
    private fun displayNameFromPrefs(): String {
        return if (Prefs.useLocation) "当前位置"
        else Prefs.cityName?.takeIf { it.isNotEmpty() } ?: "北京"
    }

    private fun tryRenderCache(json: String): Boolean {
        val parsed = parseWeather(json) ?: return false
        render(parsed, displayNameFromPrefs(), Prefs.lastWeatherTime, null)
        return true
    }

    private fun parseWeather(json: String): WeatherData? {
        return try {
            val obj = JSONObject(json)
            val current = obj.optJSONObject("current") ?: return null
            val tempC = current.getDouble("temperature_2m")
            val code = current.getInt("weather_code")
            val isDay = current.optInt("is_day", 1) != 0

            val daily = obj.optJSONObject("daily")
            val maxArr = daily?.optJSONArray("temperature_2m_max")
            val minArr = daily?.optJSONArray("temperature_2m_min")
            val highC = maxArr?.optDouble(0, tempC) ?: tempC
            val lowC = minArr?.optDouble(0, tempC) ?: tempC

            val entries = ArrayList<HourEntry>()
            val hourly = obj.optJSONObject("hourly")
            val times = hourly?.optJSONArray("time")
            if (times != null && times.length() > 0) {
                val temps = hourly?.optJSONArray("temperature_2m")
                val codes = hourly?.optJSONArray("weather_code")
                val days = hourly?.optJSONArray("is_day")
                val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                var start = 0
                for (i in 0 until times.length()) {
                    if (hourOf(times.optString(i)) == nowHour) {
                        start = i
                        break
                    }
                }
                val end = minOf(times.length(), start + 6)
                for (i in start until end) {
                    val hour = hourOf(times.optString(i)) ?: continue
                    val t = temps?.optDouble(i) ?: continue
                    val c = codes?.optInt(i) ?: continue
                    val d = days?.optInt(i, 1) != 0
                    entries.add(HourEntry(hour, t, c, d))
                }
            }

            WeatherData(
                tempC = tempC,
                conditionZh = WeatherClient.codeToZh(code, isDay),
                highC = highC,
                lowC = lowC,
                hourly = entries
            )
        } catch (e: Exception) {
            null
        }
    }

    /** "2026-11-15T22:00" → 22；格式异常返回 null */
    private fun hourOf(time: String): Int? {
        if (time.length < 13) return null
        return time.substring(11, 13).toIntOrNull()
    }

    private fun render(data: WeatherData, city: String, updatedAt: Long, note: String? = null) {
        tempView.text = fmtDeg(data.tempC)
        condView.text = data.conditionZh
        hiLoView.text = "最高 " + fmtDeg(data.highC) + " · 最低 " + fmtDeg(data.lowC)
        cityView.text = if (note != null) "$city\n$note" else city
        updatedView.text = "更新于 " +
            SimpleDateFormat("HH:mm", Locale.US).format(Date(updatedAt)) + " · Open-Meteo"
        hourlyRow.removeAllViews()
        if (data.hourly.isEmpty()) {
            hourlyRow.visibility = View.GONE
        } else {
            hourlyRow.visibility = View.VISIBLE
            for (entry in data.hourly) {
                hourlyRow.addView(buildHourColumn(entry))
            }
        }
        showContent()
    }

    private fun buildHourColumn(entry: HourEntry): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                dp(46f), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(2f)
                rightMargin = dp(2f)
            }
        }
        val hour = TextView(context).apply {
            text = "${entry.hour}时"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(StandbyColors.TERTIARY)
            gravity = Gravity.CENTER
        }
        val icon = ImageView(context).apply {
            setImageResource(iconFor(entry.code, entry.isDay))
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                topMargin = dp(6f)
                bottomMargin = dp(4f)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        val temp = TextView(context).apply {
            text = fmtDeg(entry.tempC)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(StandbyColors.WHITE)
            gravity = Gravity.CENTER
        }
        column.addView(hour)
        column.addView(icon)
        column.addView(temp)
        return column
    }

    private fun iconFor(code: Int, isDay: Boolean): Int = when (code) {
        0 -> if (isDay) R.drawable.ic_w_sun else R.drawable.ic_w_moon
        1 -> R.drawable.ic_w_cloudy
        2, 3 -> R.drawable.ic_w_cloud
        45, 48 -> R.drawable.ic_w_fog
        in 51..67, in 80..82 -> R.drawable.ic_w_rain
        in 71..77, 85, 86 -> R.drawable.ic_w_snow
        in 95..99 -> R.drawable.ic_w_thunder
        else -> R.drawable.ic_w_cloud
    }

    private fun fmtDeg(celsius: Double): String =
        String.format(Locale.US, "%d°", Math.round(celsius))

    private fun showStatus(text: String) {
        statusView.text = text
        statusView.visibility = View.VISIBLE
        contentView.visibility = View.GONE
    }

    private fun showContent() {
        contentView.visibility = View.VISIBLE
        statusView.visibility = View.GONE
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private class WeatherData(
        val tempC: Double,
        val conditionZh: String,
        val highC: Double,
        val lowC: Double,
        val hourly: List<HourEntry>
    )

    private class HourEntry(val hour: Int, val tempC: Double, val code: Int, val isDay: Boolean)

    private class LocationTarget(
        val lat: Double,
        val lon: Double,
        val display: String,
        val note: String? = null
    )
}
