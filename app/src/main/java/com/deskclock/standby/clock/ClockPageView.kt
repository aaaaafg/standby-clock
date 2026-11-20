package com.deskclock.standby.clock

import android.content.Context
import android.graphics.Typeface
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.deskclock.standby.Prefs
import com.deskclock.standby.StandbyColors
import com.deskclock.standby.StandbyPage
import com.deskclock.standby.weather.WeatherClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/**
 * 时钟页：大数字时钟 + 日期 + 星期 + 电池电量 + 天气摘要。
 * iOS StandBy 布局：顶部日期/电池，中央超大时间，底部信息。
 *
 * 字号不再用 TextView 自适应（放在 weight 容器里首次测量宽度为 0 会取最小字号，
 * 导致"字太小"），改为按屏幕尺寸精确计算像素字号，并乘以用户可调的缩放比例。
 */
class ClockPageView(context: Context) : FrameLayout(context), StandbyPage {

    private val dateText: TextView
    private val batteryText: TextView
    private val batteryView: BatteryView
    private val timeText: TextView
    private val secondsText: TextView
    private val ampmText: TextView
    private val weatherLine: TextView
    private val timeRow: LinearLayout

    private var tickCount = 0
    private var lastDate: LocalDate = LocalDateTime.now().toLocalDate()
    private var lastScale = -1
    private var lastTimeSizePx = 0f

    private val dateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val hhmm24 = DateTimeFormatter.ofPattern("HH:mm")
    private val hhmm12 = DateTimeFormatter.ofPattern("h:mm")
    private val ssFmt = DateTimeFormatter.ofPattern(":ss")

    init {
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(28), dp(16), dp(28), dp(48))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // ---- 顶部行：日期（左）· 电池（右） ----
        val top = LinearLayout(context)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        root.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        dateText = TextView(context)
        dateText.setTextColor(StandbyColors.SECONDARY)
        dateText.typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
        dateText.textSize = 16f
        dateText.text = LocalDateTime.now().format(dateFmt)
        top.addView(dateText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        batteryText = TextView(context)
        batteryText.setTextColor(StandbyColors.SECONDARY)
        batteryText.typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
        batteryText.textSize = 15f
        batteryText.text = "100%"
        val btlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btlp.gravity = Gravity.CENTER_VERTICAL
        top.addView(batteryText, btlp)

        batteryView = BatteryView(context)
        val bvlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bvlp.gravity = Gravity.CENTER_VERTICAL
        bvlp.leftMargin = dp(6)
        top.addView(batteryView, bvlp)

        // ---- 中央：超大时间（+ 小号秒 / 上午下午） ----
        timeRow = LinearLayout(context)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.BOTTOM
        val trlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        trlp.topMargin = dp(6)
        root.addView(timeRow, trlp)

        timeText = TextView(context)
        timeText.setTextColor(StandbyColors.fontColor())
        timeText.typeface = Typeface.create(StandbyColors.TYPEFACE_LIGHT, Typeface.NORMAL)
        timeText.gravity = Gravity.CENTER
        timeText.maxLines = 1
        timeText.includeFontPadding = false
        timeText.letterSpacing = -0.01f
        timeText.text = "--:--"
        timeRow.addView(timeText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        ampmText = TextView(context)
        ampmText.setTextColor(StandbyColors.SECONDARY)
        ampmText.typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
        ampmText.textSize = 16f
        ampmText.text = "上午"
        ampmText.visibility = View.GONE
        val amlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        amlp.gravity = Gravity.BOTTOM
        amlp.bottomMargin = dp(14)
        amlp.leftMargin = dp(10)
        timeRow.addView(ampmText, amlp)

        secondsText = TextView(context)
        secondsText.setTextColor(StandbyColors.fontColor())
        secondsText.typeface = Typeface.create(StandbyColors.TYPEFACE_LIGHT, Typeface.NORMAL)
        secondsText.textSize = 40f
        secondsText.text = ":00"
        val sslp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sslp.gravity = Gravity.BOTTOM
        sslp.bottomMargin = dp(12)
        sslp.leftMargin = dp(8)
        timeRow.addView(secondsText, sslp)

        // ---- 天气摘要一行 ----
        weatherLine = TextView(context)
        weatherLine.setTextColor(StandbyColors.TERTIARY)
        weatherLine.typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
        weatherLine.textSize = 15f
        weatherLine.gravity = Gravity.CENTER
        val wlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        wlp.topMargin = dp(4)
        root.addView(weatherLine, wlp)

        updateBattery()
        updateWeatherLine()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) updateTimeSize()
    }

    /**
     * 按屏幕尺寸计算超大时间字号：
     * - 宽度："HH:mm" 约 2.35em + 秒约 0.5em，取可用宽度 / 2.85
     * - 高度：不超过页面高度的 50%
     * - 乘以用户在设置里调节的缩放百分比（默认 130%）
     */
    private fun updateTimeSize() {
        if (width <= 0 || height <= 0) return
        lastScale = Prefs.clockScalePercent
        val scale = (lastScale.coerceIn(60, 180)) / 100f
        val availW = width - dp(56)
        val base = min(availW / 2.85f, height * 0.50f)
        val sizePx = (base * scale).coerceAtLeast(dp(40).toFloat())
        val clamped = min(sizePx, height * 0.62f)
        if (clamped == lastTimeSizePx) return
        lastTimeSizePx = clamped
        timeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, clamped)
        secondsText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, clamped * 0.30f)
        ampmText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, clamped * 0.13f)
        val sslp = secondsText.layoutParams as? LinearLayout.LayoutParams
        if (sslp != null) {
            sslp.bottomMargin = (clamped * 0.10f).toInt()
            secondsText.layoutParams = sslp
        }
        val amlp = ampmText.layoutParams as? LinearLayout.LayoutParams
        if (amlp != null) {
            amlp.bottomMargin = (clamped * 0.12f).toInt()
            ampmText.layoutParams = amlp
        }
    }

    override fun onTick() {
        tickCount++
        val now = LocalDateTime.now()

        if (Prefs.clockScalePercent != lastScale) updateTimeSize()

        val is24 = when (Prefs.hourMode) {
            1 -> true
            2 -> false
            else -> DateFormat.is24HourFormat(context)
        }
        if (is24) {
            timeText.text = now.format(hhmm24)
            ampmText.visibility = View.GONE
        } else {
            timeText.text = now.format(hhmm12)
            ampmText.text = if (now.hour < 12) "上午" else "下午"
            ampmText.visibility = View.VISIBLE
        }

        if (Prefs.showSeconds) {
            secondsText.visibility = View.VISIBLE
            secondsText.text = now.format(ssFmt)
        } else {
            secondsText.visibility = View.INVISIBLE
        }

        val today = now.toLocalDate()
        if (today != lastDate) {
            lastDate = today
            dateText.text = now.format(dateFmt)
        }

        if (tickCount % 5 == 0) updateBattery()
        if (tickCount % 30 == 0) updateWeatherLine()
    }

    override fun onPageShown() {
        updateBattery()
        updateWeatherLine()
        if (Prefs.clockScalePercent != lastScale) updateTimeSize()
    }

    override fun view(): View = this

    private fun updateBattery() {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            val charging = bm?.isCharging == true
            batteryView.level = level.coerceIn(0, 100)
            batteryView.charging = charging
            batteryView.invalidate()
            batteryText.text = "${batteryView.level}%"
        } catch (e: Exception) {
            batteryText.text = "--%"
        }
    }

    private fun updateWeatherLine() {
        val s = WeatherClient.parseSummary(Prefs.lastWeatherJson)
        weatherLine.text = if (s != null) {
            val city = if (s.city.isBlank()) "" else " · ${s.city}"
            "${s.tempC.toInt()}° ${s.conditionZh}$city"
        } else {
            ""
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
