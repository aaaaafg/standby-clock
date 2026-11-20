package com.deskclock.standby.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * 天气数据摘要（供时钟页等其他页面直接使用）。
 * 由 [WeatherClient.parseSummary] 从缓存 JSON 解析得到。
 */
data class WeatherSummary(val tempC: Double, val conditionZh: String, val city: String)

/**
 * Open-Meteo 客户端：仅用 java.net + org.json，不依赖 Android、无第三方库。
 * [fetchForecastJson] 与 [searchCity] 是同步网络方法，调用方负责放到后台线程。
 */
object WeatherClient {

    private const val TIMEOUT_MS = 10_000

    /**
     * 从 lastWeatherJson 缓存字符串解析摘要；解析失败返回 null。
     * 纯函数、不依赖 Android。JSON 中不含城市信息，city 固定返回空字符串，
     * 显示城市名由调用方决定。
     */
    fun parseSummary(json: String?): WeatherSummary? {
        if (json.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(json)
            val current = obj.optJSONObject("current") ?: return null
            if (!current.has("temperature_2m")) return null
            val code = current.optInt("weather_code", 0)
            val isDay = current.optInt("is_day", 1) != 0
            WeatherSummary(
                tempC = current.getDouble("temperature_2m"),
                conditionZh = codeToZh(code, isDay),
                city = ""
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * WMO 天气码 → 中文。
     * isDay（当前是否白天）不影响文案，保留给页面选择日夜图标（晴夜用月亮）。
     */
    fun codeToZh(code: Int, isDay: Boolean): String = when (code) {
        0 -> "晴"
        1 -> "晴间多云"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57, 66, 67 -> "冻雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "霰"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷暴"
        96, 99 -> "雷暴伴冰雹"
        else -> "未知"
    }

    /**
     * 请求 Open-Meteo 并返回原始 JSON（同步方法，调用方负责放后台线程）；失败抛异常。
     */
    fun fetchForecastJson(lat: Double, lon: Double): String {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            lat,
            lon
        )
        return httpGet(url)
    }

    /**
     * 城市名 → 城市结果（name/lat/lon）；失败返回 null。同步方法。
     */
    fun searchCity(name: String): CityResult? {
        val query = name.trim()
        if (query.isEmpty()) return null
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") +
            "&count=1&language=zh&format=json"
        return try {
            val obj = JSONObject(httpGet(url))
            val results = obj.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            CityResult(
                name = first.optString("name", query),
                latitude = first.getDouble("latitude"),
                longitude = first.getDouble("longitude")
            )
        } catch (e: Exception) {
            null
        }
    }

    data class CityResult(val name: String, val latitude: Double, val longitude: Double)

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.isBlank()) {
                throw IOException("empty response")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
