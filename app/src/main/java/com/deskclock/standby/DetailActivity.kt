package com.deskclock.standby

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import com.deskclock.standby.perm.PermissionChecker
import com.deskclock.standby.perm.SettingsJumper
import com.deskclock.standby.weather.WeatherClient
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 设置子页面（iOS「设置」App 风格）：顶部导航栏 + 卡片组。
 * section：style 外观与亮度 / burnin 防烧屏 / weather 天气与定位 /
 *          permissions 权限与澎湃OS适配 / guide 使用说明 / diagnostics 诊断信息。
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTION = "section"

        private const val IOS_BLUE = 0xFF0A84FF.toInt()
        private const val IOS_GREEN = 0xFF34C759.toInt()
        private const val IOS_RED = 0xFFFF3B30.toInt()
        private const val CHEVRON_GRAY = 0xFF8E8E93.toInt()
        private const val SEPARATOR = 0xFF3A3A3C.toInt()
    }

    private val colorPresets = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFF2EFE6.toInt(), 0xFFFFD9A0.toInt(),
        0xFFA8D8FF.toInt(), 0xFFBFF4E9.toInt(), 0xFFFF6B5E.toInt()
    )

    private var section = "style"
    private var swatchRow: LinearLayout? = null
    private val hourChecks = mutableListOf<TextView>()
    private var permCard: LinearLayout? = null
    private var cityInput: EditText? = null
    private var currentCityTv: TextView? = null
    private var diagTv: TextView? = null
    private var diagLiveTv: TextView? = null
    private var diagMonitor: StandbyStateMonitor? = null
    private var diagHandler: android.os.Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        section = intent.getStringExtra(EXTRA_SECTION) ?: "style"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(
            buildNavBar(titleFor(section)),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        )

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(4), 0, dp(48))
        scroll.addView(
            content,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        when (section) {
            "style" -> buildStylePage(content)
            "burnin" -> buildBurnInPage(content)
            "weather" -> buildWeatherPage(content)
            "permissions" -> buildPermissionsPage(content)
            "guide" -> buildGuidePage(content)
            "diagnostics" -> buildDiagnosticsPage(content)
            else -> buildStylePage(content)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
    }

    private fun titleFor(section: String): String = when (section) {
        "style" -> "外观与亮度"
        "burnin" -> "防烧屏"
        "weather" -> "天气与定位"
        "permissions" -> "权限与澎湃OS适配"
        "guide" -> "使用说明"
        "diagnostics" -> "诊断信息"
        else -> "设置"
    }

    // ================= 顶部导航栏 =================

    /** 左侧「‹」44dp 点按区 + 居中标题 17sp medium，高 52dp */
    private fun buildNavBar(title: String): FrameLayout {
        val bar = FrameLayout(this)
        val back = TextView(this)
        back.text = "‹"
        back.setTextColor(StandbyColors.WHITE)
        back.textSize = 20f
        back.gravity = Gravity.CENTER
        back.setOnClickListener { finish() }
        val blp = FrameLayout.LayoutParams(dp(44), dp(44))
        blp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        bar.addView(back, blp)

        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 17f
        tv.typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
        tv.gravity = Gravity.CENTER
        val tlp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        tlp.gravity = Gravity.CENTER
        bar.addView(tv, tlp)
        return bar
    }

    // ================= 页面：style 外观与亮度 =================

    private fun buildStylePage(content: LinearLayout) {
        // 卡片 1：字体颜色（标题行 + 色块行）
        val card1 = newCard()
        card1.addView(titleRow("字体颜色"), rowLp())
        addSeparator(card1)
        val srow = LinearLayout(this)
        srow.orientation = LinearLayout.HORIZONTAL
        srow.gravity = Gravity.CENTER_VERTICAL
        srow.setPadding(dp(16), 0, dp(16), 0)
        swatchRow = srow
        buildSwatches()
        card1.addView(srow, rowLp())
        addCard(content, card1)

        // 卡片 2：亮度与秒
        val card2 = newCard()
        val follow = SwitchCompat(this)
        styleSwitch(follow)
        follow.isChecked = Prefs.standbyBrightnessPercent < 0
        card2.addView(switchRowView("跟随系统亮度", follow), rowLp())
        addSeparator(card2)

        val sliderRow = LinearLayout(this)
        sliderRow.orientation = LinearLayout.HORIZONTAL
        sliderRow.gravity = Gravity.CENTER_VERTICAL
        sliderRow.setPadding(dp(16), 0, dp(16), 0)
        val sliderLabel = TextView(this)
        sliderLabel.text = "待机亮度"
        sliderLabel.setTextColor(StandbyColors.WHITE)
        sliderLabel.textSize = 16f
        sliderRow.addView(sliderLabel)
        val brightnessSeek = SeekBar(this)
        brightnessSeek.min = 2
        brightnessSeek.max = 100
        brightnessSeek.progress =
            if (Prefs.standbyBrightnessPercent >= 0) Prefs.standbyBrightnessPercent.coerceIn(2, 100) else 80
        brightnessSeek.isEnabled = Prefs.standbyBrightnessPercent >= 0
        brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) Prefs.standbyBrightnessPercent = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        follow.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                Prefs.standbyBrightnessPercent = -1
                brightnessSeek.isEnabled = false
            } else {
                Prefs.standbyBrightnessPercent = brightnessSeek.progress
                brightnessSeek.isEnabled = true
            }
        }
        sliderRow.addView(brightnessSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card2.addView(sliderRow, rowLp())
        addSeparator(card2)

        val showSeconds = SwitchCompat(this)
        styleSwitch(showSeconds)
        showSeconds.isChecked = Prefs.showSeconds
        showSeconds.setOnCheckedChangeListener { _, checked -> Prefs.showSeconds = checked }
        card2.addView(switchRowView("显示秒", showSeconds), rowLp())
        addSeparator(card2)

        // 时钟数字大小（60%-180%），实时写入设置，回到时钟页即生效
        val sizeRow = LinearLayout(this)
        sizeRow.orientation = LinearLayout.HORIZONTAL
        sizeRow.gravity = Gravity.CENTER_VERTICAL
        sizeRow.setPadding(dp(16), 0, dp(16), 0)
        val sizeLabel = TextView(this)
        sizeLabel.text = "时钟大小"
        sizeLabel.setTextColor(StandbyColors.WHITE)
        sizeLabel.textSize = 16f
        sizeRow.addView(sizeLabel)
        val sizeSeek = SeekBar(this)
        sizeSeek.min = 60
        sizeSeek.max = 180
        sizeSeek.progress = Prefs.clockScalePercent.coerceIn(60, 180)
        val sizeValue = TextView(this)
        sizeValue.text = "${sizeSeek.progress}%"
        sizeValue.setTextColor(StandbyColors.SECONDARY)
        sizeValue.textSize = 13f
        sizeValue.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Prefs.clockScalePercent = progress
                    sizeValue.text = "$progress%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        sizeRow.addView(sizeSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sizeRow.addView(sizeValue, LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT))
        card2.addView(sizeRow, rowLp())
        addCard(content, card2)

        // 卡片 3：时间制式（3 个可选行，选中行右侧绿色 ✓）
        val card3 = newCard()
        card3.addView(titleRow("时间制式"), rowLp())
        addSeparator(card3)
        val options = arrayOf("跟随系统", "24 小时制", "12 小时制")
        hourChecks.clear()
        options.forEachIndexed { i, label ->
            card3.addView(optionRow(label, Prefs.hourMode == i) {
                Prefs.hourMode = i
                refreshHourChecks()
            }, rowLp())
            if (i < options.size - 1) addSeparator(card3)
        }
        addCard(content, card3)
    }

    private fun buildSwatches() {
        val row = swatchRow ?: return
        colorPresets.forEach { c ->
            val dot = View(this)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(c)
            dot.background = bg
            val lp = LinearLayout.LayoutParams(dp(30), dp(30))
            lp.rightMargin = dp(10)
            lp.gravity = Gravity.CENTER_VERTICAL
            row.addView(dot, lp)
            dot.setOnClickListener {
                Prefs.fontColor = c
                refreshSwatchStrokes()
                toast("字体颜色已更新")
            }
        }
        val custom = TextView(this)
        custom.text = "自定义"
        custom.setTextColor(StandbyColors.WHITE)
        custom.textSize = 13f
        custom.gravity = Gravity.CENTER
        custom.setPadding(dp(14), 0, dp(14), 0)
        val cbg = GradientDrawable()
        cbg.shape = GradientDrawable.RECTANGLE
        cbg.cornerRadius = dp(16).toFloat()
        cbg.setColor(0x00FFFFFF)
        cbg.setStroke(dp(1), StandbyColors.SECONDARY)
        custom.background = cbg
        custom.setOnClickListener { showCustomColorDialog() }
        val clp = LinearLayout.LayoutParams(dp(84), dp(34))
        clp.gravity = Gravity.CENTER_VERTICAL
        row.addView(custom, clp)
        refreshSwatchStrokes()
    }

    /** 选中色块：红色描边；未选中：细白描边 */
    private fun refreshSwatchStrokes() {
        val row = swatchRow ?: return
        for (i in 0 until colorPresets.size) {
            val dot = row.getChildAt(i) ?: continue
            val bg = dot.background as? GradientDrawable ?: continue
            val selected = Prefs.fontColor == colorPresets[i]
            bg.setStroke(if (selected) dp(3) else dp(1), if (selected) StandbyColors.RED else 0x33FFFFFF)
        }
    }

    private fun refreshHourChecks() {
        hourChecks.forEachIndexed { i, tv ->
            tv.visibility = if (Prefs.hourMode == i) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun showCustomColorDialog() {
        val input = EditText(this)
        input.setTextColor(StandbyColors.WHITE)
        input.setHintTextColor(StandbyColors.TERTIARY)
        input.hint = "#RRGGBB"
        input.setText(String.format(Locale.US, "#%06X", Prefs.fontColor and 0xFFFFFF))
        val pad = dp(20)
        input.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this)
            .setTitle("自定义字体颜色")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val hex = input.text.toString().trim()
                try {
                    Prefs.fontColor = Color.parseColor(hex)
                    refreshSwatchStrokes()
                    toast("字体颜色已更新")
                } catch (e: Exception) {
                    toast("颜色格式无效")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ================= 页面：burnin 防烧屏 =================

    private fun buildBurnInPage(content: LinearLayout) {
        val card = newCard()
        val anti = SwitchCompat(this)
        styleSwitch(anti)
        anti.isChecked = Prefs.antiBurnIn
        anti.setOnCheckedChangeListener { _, checked -> Prefs.antiBurnIn = checked }
        card.addView(switchRowView("像素缓慢偏移", anti), rowLp())
        addSeparator(card)

        val night = SwitchCompat(this)
        styleSwitch(night)
        night.isChecked = Prefs.nightDimEnabled
        night.setOnCheckedChangeListener { _, checked -> Prefs.nightDimEnabled = checked }
        card.addView(switchRowView("夜间低亮度", night), rowLp())
        addSeparator(card)

        val startValue = valueTv(minuteLabel(Prefs.nightStartMinute))
        card.addView(timeRow("开始", startValue) {
            pickTime(Prefs.nightStartMinute) { m ->
                Prefs.nightStartMinute = m
                startValue.text = minuteLabel(m)
            }
        }, rowLp())
        addSeparator(card)

        val endValue = valueTv(minuteLabel(Prefs.nightEndMinute))
        card.addView(timeRow("结束", endValue) {
            pickTime(Prefs.nightEndMinute) { m ->
                Prefs.nightEndMinute = m
                endValue.text = minuteLabel(m)
            }
        }, rowLp())
        addSeparator(card)

        val sliderRow = LinearLayout(this)
        sliderRow.orientation = LinearLayout.HORIZONTAL
        sliderRow.gravity = Gravity.CENTER_VERTICAL
        sliderRow.setPadding(dp(16), 0, dp(16), 0)
        val sliderLabel = TextView(this)
        sliderLabel.text = "夜间亮度"
        sliderLabel.setTextColor(StandbyColors.WHITE)
        sliderLabel.textSize = 16f
        sliderRow.addView(sliderLabel)
        val nightSeek = SeekBar(this)
        nightSeek.min = 2
        nightSeek.max = 30
        nightSeek.progress = Prefs.nightBrightnessPercent.coerceIn(2, 30)
        nightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) Prefs.nightBrightnessPercent = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        sliderRow.addView(nightSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(sliderRow, rowLp())
        addCard(content, card)
    }

    // ================= 页面：weather 天气与定位 =================

    private fun buildWeatherPage(content: LinearLayout) {
        val card = newCard()
        val useLoc = SwitchCompat(this)
        styleSwitch(useLoc)
        useLoc.isChecked = Prefs.useLocation
        useLoc.setOnCheckedChangeListener { _, checked -> Prefs.useLocation = checked }
        card.addView(switchRowView("使用定位获取天气", useLoc), rowLp())
        addSeparator(card)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)
        cityInput = EditText(this)
        cityInput!!.setTextColor(StandbyColors.WHITE)
        cityInput!!.setHintTextColor(StandbyColors.TERTIARY)
        cityInput!!.hint = "输入城市名，如：上海"
        cityInput!!.inputType = InputType.TYPE_CLASS_TEXT
        cityInput!!.background = null
        row.addView(cityInput!!, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btn = TextView(this)
        btn.text = "搜索"
        btn.setTextColor(IOS_BLUE)
        btn.textSize = 15f
        btn.typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
        btn.setPadding(dp(14), 0, 0, 0)
        btn.setOnClickListener { searchCity() }
        row.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(row, rowLp())
        addSeparator(card)

        val infoRow = LinearLayout(this)
        infoRow.orientation = LinearLayout.HORIZONTAL
        infoRow.gravity = Gravity.CENTER_VERTICAL
        infoRow.setPadding(dp(16), 0, dp(16), 0)
        currentCityTv = TextView(this)
        currentCityTv!!.text = citySummary()
        currentCityTv!!.setTextColor(StandbyColors.TERTIARY)
        currentCityTv!!.textSize = 12f
        infoRow.addView(currentCityTv!!)
        card.addView(infoRow, rowLp())
        addCard(content, card)

        // 测试天气：立即拉取一次并提示结果，便于排查天气页问题
        val testCard = newCard()
        val testBtn = TextView(this)
        testBtn.text = "测试天气（立即拉取一次）"
        testBtn.setTextColor(IOS_BLUE)
        testBtn.textSize = 16f
        testBtn.gravity = Gravity.CENTER
        testBtn.setPadding(dp(16), 0, dp(16), 0)
        testBtn.setOnClickListener { testWeather() }
        testCard.addView(testBtn, rowLp())
        addCard(content, testCard)
    }

    /** 测试天气数据源：返回成功温度或具体失败原因 */
    private fun testWeather() {
        toast("正在测试天气…")
        Thread {
            val msg = try {
                val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val hasNet: Boolean = if (cm == null) {
                    true
                } else {
                    val net = cm.activeNetwork
                    val caps = if (net != null) cm.getNetworkCapabilities(net) else null
                    caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
                if (!hasNet) {
                    "无网络连接"
                } else {
                    val json = WeatherClient.fetchForecastJson(
                        Prefs.cityLat.toDouble(),
                        Prefs.cityLon.toDouble()
                    )
                    val s = WeatherClient.parseSummary(json)
                    if (s != null) "成功：${s.tempC.toInt()}° ${s.conditionZh}（坐标：${Prefs.cityLat}/${Prefs.cityLon}）"
                    else "数据解析失败"
                }
            } catch (e: Exception) {
                "失败：${e.javaClass.simpleName} ${e.message}"
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                toast(msg)
            }
        }.start()
    }

    private fun citySummary(): String =
        Prefs.cityName?.let { "当前城市：$it" } ?: "当前城市：未设置（默认北京）"

    private fun searchCity() {
        val name = cityInput?.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            toast("请输入城市名")
            return
        }
        toast("正在搜索…")
        Thread {
            val result = try {
                WeatherClient.searchCity(name)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result != null) {
                    Prefs.setCity(result.name, result.latitude, result.longitude)
                    toast("已设置城市：${result.name}")
                    currentCityTv?.text = citySummary()
                } else {
                    toast("搜索失败，请检查网络")
                }
            }
        }.start()
    }

    // ================= 页面：permissions 权限与澎湃OS适配 =================

    private fun buildPermissionsPage(content: LinearLayout) {
        val desc = TextView(this)
        desc.text = "以下权限影响待机模式的可靠性。点击「去开启」可直达对应系统设置页，返回本页后状态自动刷新。"
        desc.setTextColor(StandbyColors.TERTIARY)
        desc.textSize = 13f
        val dlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dlp.leftMargin = dp(32)
        dlp.rightMargin = dp(32)
        dlp.topMargin = dp(18)
        dlp.bottomMargin = dp(8)
        content.addView(desc, dlp)

        permCard = newCard()
        addCard(content, permCard!!)

        val quick = newCard()
        val tv = TextView(this)
        tv.text = quickReferenceText()
        tv.setTextColor(StandbyColors.SECONDARY)
        tv.textSize = 12f
        tv.setPadding(dp(16), dp(14), dp(16), dp(14))
        quick.addView(tv)
        addCard(content, quick)

        refreshPermissions()
    }

    /** 整块权限区 removeAllViews 重建 */
    private fun refreshPermissions() {
        val card = permCard ?: return
        card.removeAllViews()
        var first = true
        fun addRow(v: View) {
            if (!first) addSeparator(card)
            first = false
            card.addView(v, rowLp())
        }

        // 1. 悬浮窗显示
        val overlayOk = PermissionChecker.overlay(this)
        addRow(
            permRow(
                "悬浮窗显示",
                if (overlayOk) PermissionChecker.Status.GRANTED else PermissionChecker.Status.DENIED,
                if (overlayOk) "已开启" else "未开启",
                if (!overlayOk) { { SettingsJumper.openOverlay(this) } } else null
            )
        )

        // 2. 后台弹出界面（仅小米系）
        if (RomInfo.isXiaomi) {
            val s = PermissionChecker.backgroundPopup(this)
            val txt = when (s) {
                PermissionChecker.Status.GRANTED -> "已开启"
                PermissionChecker.Status.DENIED -> "未开启"
                else -> "无法自动检测"
            }
            addRow(
                permRow(
                    "后台弹出界面",
                    s,
                    txt,
                    if (s != PermissionChecker.Status.GRANTED) { { SettingsJumper.openBackgroundPopup(this) } } else null
                )
            )

            // 2b. 锁屏显示（仅小米系，无法自动检测，需人工确认；影响充电时能否在锁屏上弹出时钟）
            addRow(
                permRow(
                    "锁屏显示",
                    PermissionChecker.Status.UNKNOWN,
                    "请手动确认",
                    { SettingsJumper.openAppDetails(this) }
                )
            )
        }

        // 3. 自启动（仅小米系）
        if (RomInfo.isXiaomi) {
            val s = PermissionChecker.autoStart(this)
            val txt = when (s) {
                PermissionChecker.Status.GRANTED -> "已开启"
                PermissionChecker.Status.DENIED -> "未开启"
                else -> "无法自动检测"
            }
            addRow(
                permRow(
                    "自启动",
                    s,
                    txt,
                    if (s != PermissionChecker.Status.GRANTED) { { SettingsJumper.openAutoStart(this) } } else null
                )
            )
        }

        // 4. 省电策略（电池不优化）
        val batOk = PermissionChecker.batteryOptimization(this)
        addRow(
            permRow(
                "省电策略（电池不优化）",
                if (batOk) PermissionChecker.Status.GRANTED else PermissionChecker.Status.DENIED,
                if (batOk) "已设置无限制" else "未设置",
                if (!batOk) { { SettingsJumper.openBatteryOptimization(this) } } else null
            )
        )

        // 5. 通知权限
        val notifOk = PermissionChecker.notifications(this)
        addRow(
            permRow(
                "通知权限",
                if (notifOk) PermissionChecker.Status.GRANTED else PermissionChecker.Status.DENIED,
                if (notifOk) "已开启" else "未开启",
                if (!notifOk) { { SettingsJumper.openNotification(this) } } else null
            )
        )

        // 6. 日历（日程）
        val cal = PermissionChecker.calendar(this)
        addRow(
            permRow(
                "日历（日程）",
                cal,
                if (cal == PermissionChecker.Status.GRANTED) "已授权" else "未授权",
                if (cal != PermissionChecker.Status.GRANTED) {
                    { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR), 1) }
                } else null
            )
        )

        // 7. 定位（天气）
        val loc = PermissionChecker.location(this)
        addRow(
            permRow(
                "定位（天气）",
                loc,
                if (loc == PermissionChecker.Status.GRANTED) "已授权" else "未授权",
                if (loc != PermissionChecker.Status.GRANTED) {
                    {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                            2
                        )
                    }
                } else null
            )
        )

        // 8. 修改系统亮度（可选）
        val wsOk = PermissionChecker.writeSettings(this)
        addRow(
            permRow(
                "修改系统亮度（可选）",
                if (wsOk) PermissionChecker.Status.GRANTED else PermissionChecker.Status.UNKNOWN,
                if (wsOk) "已授权" else "未授权",
                if (!wsOk) { { SettingsJumper.openWriteSettings(this) } } else null
            )
        )

        // 9. 无需授权的系统接口
        addRow(infoRow("读取电池电量"))
        addRow(infoRow("重力传感器"))
    }

    /** 权限行：8dp 状态圆点 + 名称 + 右侧状态文字/「去开启」按钮 */
    private fun permRow(
        title: String,
        status: PermissionChecker.Status,
        statusText: String,
        onClick: (() -> Unit)?
    ): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)

        val dot = View(this)
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(statusColor(status))
        dot.background = d
        row.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)))

        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        val tlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(10)
        row.addView(tv, tlp)

        val st = TextView(this)
        st.text = statusText
        st.setTextColor(statusColor(status))
        st.textSize = 12f
        row.addView(st, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        if (onClick != null) {
            val btn = TextView(this)
            btn.text = "去开启"
            btn.setTextColor(StandbyColors.WHITE)
            btn.textSize = 12f
            btn.gravity = Gravity.CENTER
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.RECTANGLE
            bg.cornerRadius = dp(14).toFloat()
            bg.setColor(0x330A84FF)
            bg.setStroke(dp(1), IOS_BLUE)
            btn.background = bg
            btn.setPadding(dp(12), 0, dp(12), 0)
            btn.setOnClickListener { onClick() }
            val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30))
            blp.leftMargin = dp(10)
            row.addView(btn, blp)
        }
        return row
    }

    private fun infoRow(title: String): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)

        val dot = View(this)
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(IOS_GREEN)
        dot.background = d
        row.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)))

        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        val tlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(10)
        row.addView(tv, tlp)

        val st = TextView(this)
        st.text = "系统接口，无需授权"
        st.setTextColor(StandbyColors.SECONDARY)
        st.textSize = 12f
        row.addView(st, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun statusColor(status: PermissionChecker.Status): Int = when (status) {
        PermissionChecker.Status.GRANTED -> IOS_GREEN
        PermissionChecker.Status.DENIED -> IOS_RED
        else -> CHEVRON_GRAY
    }

    /** 底部速查：澎湃OS4 完整手动路径；非小米设备显示通用安卓路径版 */
    private fun quickReferenceText(): String {
        return if (RomInfo.isXiaomi) {
            val prefix = when {
                RomInfo.isHyperOs4 -> "澎湃OS4 手动设置路径：\n"
                RomInfo.isHyperOs -> "澎湃OS 手动设置路径：\n"
                else -> "MIUI 手动设置路径：\n"
            }
            prefix + """
                • 悬浮窗：设置 → 应用设置 → 应用管理 → 待机时钟 → 权限管理 → 显示悬浮窗（允许）
                • 后台弹出界面：设置 → 应用设置 → 应用管理 → 待机时钟 → 权限管理 → 后台弹出界面（允许）
                • 锁屏显示：设置 → 应用设置 → 应用管理 → 待机时钟 → 权限管理 → 其他权限 → 锁屏显示（允许）
                • 自启动：设置 → 应用设置 → 授权管理 → 自启动管理 → 待机时钟（允许）
                • 省电策略：设置 → 应用设置 → 应用管理 → 待机时钟 → 省电策略 → 无限制
                • 通知：设置 → 通知与控制中心 → 应用通知管理 → 待机时钟 → 允许通知
                • 日历：设置 → 应用设置 → 应用管理 → 待机时钟 → 权限管理 → 日历（允许）
                • 定位：设置 → 应用设置 → 应用管理 → 待机时钟 → 权限管理 → 定位（仅使用期间允许）
                • 修改系统亮度：设置 → 应用设置 → 应用管理 → 待机时钟 → 权限管理 → 其他权限 → 修改系统设置（允许，可选）
            """.trimIndent()
        } else {
            "通用安卓 手动设置路径：\n" + """
                • 悬浮窗：设置 → 应用 → 特殊应用权限 → 显示在其他应用上层 → 待机时钟（允许）
                • 省电策略：设置 → 电池 → 电池优化 → 待机时钟 → 不优化
                • 通知：设置 → 应用 → 通知 → 待机时钟 → 允许通知
                • 日历：设置 → 应用 → 待机时钟 → 权限 → 日历（允许）
                • 定位：设置 → 应用 → 待机时钟 → 权限 → 定位（仅使用期间允许）
                • 修改系统亮度：设置 → 应用 → 特殊应用权限 → 修改系统设置（允许，可选）
            """.trimIndent()
        }
    }

    // ================= 页面：guide 使用说明 =================

    private fun buildGuidePage(content: LinearLayout) {
        val card = newCard()
        val tv = TextView(this)
        tv.text = """
            1. 开启待机模式并授权全部权限。
            2. 手机充电时横放（侧立或横躺），约 2 秒后自动显示全屏时钟。
            3. 左右滑动切换：时钟页 / 日历任务页 / 天气页。
            4. 拔掉充电线或转回竖屏，时钟自动关闭并回到锁屏。
            5. 双击屏幕可手动退出（本次充电内不再自动弹出）。
            6. 日历任务页读取系统日历当日日程；天气来自 Open-Meteo（免费数据）。
        """.trimIndent()
        tv.setTextColor(StandbyColors.SECONDARY)
        tv.textSize = 12f
        tv.setPadding(dp(16), dp(14), dp(16), dp(14))
        card.addView(tv)
        addCard(content, card)

        val btnCard = newCard()
        val btn = TextView(this)
        btn.text = "导出当前配置（JSON）"
        btn.setTextColor(IOS_BLUE)
        btn.textSize = 16f
        btn.gravity = Gravity.CENTER
        btn.setPadding(dp(16), 0, dp(16), 0)
        btn.setOnClickListener { exportConfig() }
        btnCard.addView(btn, rowLp())
        addCard(content, btnCard)
    }

    private fun exportConfig() {
        try {
            val json = JSONObject()
            json.put("enabled", Prefs.enabled)
            json.put("bootStart", Prefs.bootStart)
            json.put("overlayFallback", Prefs.overlayFallback)
            json.put("exitOnUnplug", Prefs.exitOnUnplug)
            json.put("exitOnPortrait", Prefs.exitOnPortrait)
            json.put("fontColor", String.format(Locale.US, "#%06X", Prefs.fontColor and 0xFFFFFF))
            json.put("showSeconds", Prefs.showSeconds)
            json.put("hourMode", Prefs.hourMode)
            json.put("standbyBrightnessPercent", Prefs.standbyBrightnessPercent)
            json.put("antiBurnIn", Prefs.antiBurnIn)
            json.put("nightDimEnabled", Prefs.nightDimEnabled)
            json.put("nightStartMinute", Prefs.nightStartMinute)
            json.put("nightEndMinute", Prefs.nightEndMinute)
            json.put("nightBrightnessPercent", Prefs.nightBrightnessPercent)
            json.put("useLocation", Prefs.useLocation)
            json.put("cityName", Prefs.cityName ?: "")
            json.put("cityLat", Prefs.cityLat.toDouble())
            json.put("cityLon", Prefs.cityLon.toDouble())
            val dir = getExternalFilesDir(null) ?: filesDir
            val file = File(dir, "standby-config.json")
            file.writeText(json.toString(2))
            toast("已导出：${file.absolutePath}")
        } catch (e: Exception) {
            toast("导出失败：${e.message}")
        }
    }

    // ================= 页面：diagnostics 诊断信息 =================

    private fun buildDiagnosticsPage(content: LinearLayout) {
        // 实时检测状态（本页可见时每秒刷新；使用独立检测器，不依赖服务是否在跑）
        val liveCard = newCard()
        diagLiveTv = TextView(this)
        diagLiveTv!!.text = "正在读取传感器…"
        diagLiveTv!!.setTextColor(StandbyColors.WHITE)
        diagLiveTv!!.textSize = 15f
        diagLiveTv!!.setPadding(dp(16), dp(14), dp(16), dp(14))
        liveCard.addView(diagLiveTv!!)
        addCard(content, liveCard)

        val monitor = StandbyStateMonitor(this) { _, _ -> refreshLiveText() }
        diagMonitor = monitor
        runCatching { monitor.start() }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        diagHandler = h
        val tick = object : Runnable {
            override fun run() {
                refreshLiveText()
                h.postDelayed(this, 1000)
            }
        }
        h.postDelayed(tick, 1000)

        val card = newCard()
        diagTv = TextView(this)
        diagTv!!.text = Diagnostics.build(this)
        diagTv!!.setTextColor(StandbyColors.WHITE)
        diagTv!!.textSize = 13f
        diagTv!!.typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
        diagTv!!.setPadding(dp(16), dp(14), dp(16), dp(14))
        card.addView(diagTv!!)
        addCard(content, card)

        val btnCard = newCard()
        val test = TextView(this)
        test.text = "测试弹出时钟（走自动检测同一路径）"
        test.setTextColor(IOS_BLUE)
        test.textSize = 16f
        test.gravity = Gravity.CENTER
        test.setPadding(dp(16), 0, dp(16), 0)
        test.setOnClickListener {
            diagTv?.text = Diagnostics.build(this)
            StandbyService.testLaunch(this)
            toast("已发送弹出请求，若 3 秒内无反应请查看下方异常记录")
        }
        btnCard.addView(test, rowLp())
        addSeparator(btnCard)
        val btn = TextView(this)
        btn.text = "清空异常记录"
        btn.setTextColor(IOS_RED)
        btn.textSize = 16f
        btn.gravity = Gravity.CENTER
        btn.setPadding(dp(16), 0, dp(16), 0)
        btn.setOnClickListener {
            CrashLog.clear()
            diagTv?.text = Diagnostics.build(this)
            refreshLiveText()
            toast("已清空异常记录")
        }
        btnCard.addView(btn, rowLp())
        addCard(content, btnCard)
    }

    /** 刷新诊断页顶部实时状态：充电 + 当前放置姿态 */
    private fun refreshLiveText() {
        val tv = diagLiveTv ?: return
        val m = diagMonitor ?: return
        val charging = m.currentCharging()
        val placement = m.currentPlacement()
        val placed = placement == StandbyStateMonitor.Placement.SIDE_LANDSCAPE ||
            placement == StandbyStateMonitor.Placement.FLAT_UP
        val state = when (placement) {
            StandbyStateMonitor.Placement.SIDE_LANDSCAPE -> "侧立横放（可触发）"
            StandbyStateMonitor.Placement.FLAT_UP -> "平放朝上（可触发）"
            StandbyStateMonitor.Placement.PORTRAIT -> "竖放（不触发）"
            StandbyStateMonitor.Placement.FLAT_DOWN -> "平放朝下（不触发）"
            StandbyStateMonitor.Placement.UNKNOWN -> "识别中…"
        }
        val serviceText = if (StandbyService.serviceRunning) "运行中" else "未运行"
        tv.text = "实时检测\n" +
            "充电：${if (charging) "是" else "否"}\n" +
            "放置：$state\n" +
            "监控服务：$serviceText\n" +
            if (charging && placed) "→ 条件满足，应自动弹出时钟" else "→ 条件未满足（需同时满足：充电 + 侧立横放/平放朝上）"
    }

    // ================= 通用行构建 =================

    override fun onDestroy() {
        diagHandler?.removeCallbacksAndMessages(null)
        diagHandler = null
        runCatching { diagMonitor?.stop() }
        diagMonitor = null
        super.onDestroy()
    }

    private fun titleRow(text: String): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        row.addView(tv)
        return row
    }

    private fun switchRowView(title: String, sw: SwitchCompat): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** 可选行（时间制式）：选中时右侧显示绿色 ✓ */
    private fun optionRow(label: String, selected: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val check = TextView(this)
        check.text = "✓"
        check.setTextColor(IOS_GREEN)
        check.textSize = 17f
        check.gravity = Gravity.CENTER
        check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        row.addView(check, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        hourChecks.add(check)
        row.setOnClickListener { onClick() }
        return row
    }

    /** 时间行：左侧标签 + 右侧数值 + 灰色 chevron */
    private fun timeRow(label: String, value: TextView, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(12), 0)
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val vlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        vlp.rightMargin = dp(8)
        row.addView(value, vlp)
        val ch = TextView(this)
        ch.text = "›"
        ch.setTextColor(CHEVRON_GRAY)
        ch.textSize = 20f
        ch.gravity = Gravity.CENTER
        row.addView(ch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.setOnClickListener { onClick() }
        return row
    }

    private fun valueTv(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(StandbyColors.SECONDARY)
        tv.textSize = 16f
        return tv
    }

    private fun pickTime(initialMinute: Int, onPicked: (Int) -> Unit) {
        val h = initialMinute / 60
        val m = initialMinute % 60
        TimePickerDialog(this, { _, hh, mm -> onPicked(hh * 60 + mm) }, h, m, true).show()
    }

    // ================= iOS 风格基础件 =================

    private fun newCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.cornerRadius = dp(14).toFloat()
        gd.setColor(StandbyColors.CARD_BG)
        card.background = gd
        return card
    }

    private fun addCard(content: LinearLayout, card: LinearLayout) {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = dp(16)
        lp.rightMargin = dp(16)
        lp.bottomMargin = dp(20)
        content.addView(card, lp)
    }

    private fun rowLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))

    private fun addSeparator(card: LinearLayout) {
        val v = View(this)
        v.setBackgroundColor(SEPARATOR)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sepHeight())
        lp.leftMargin = dp(16)
        card.addView(v, lp)
    }

    /** iOS 绿开关：白色滑钮 + 绿色轨道 */
    private fun styleSwitch(sw: SwitchCompat) {
        sw.setTextColor(StandbyColors.WHITE)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(StandbyColors.WHITE, CHEVRON_GRAY))
        sw.trackTintList = ColorStateList(states, intArrayOf(IOS_GREEN, 0xFF39393D.toInt()))
    }

    // ================= 小工具 =================

    private fun minuteLabel(minute: Int): String =
        String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)

    private fun sepHeight(): Int =
        ((0.5f * resources.displayMetrics.density) + 0.5f).toInt().coerceAtLeast(1)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
