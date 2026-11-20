package com.deskclock.standby

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.deskclock.standby.perm.PermissionChecker
import java.util.Locale

/**
 * 主界面：iOS「设置」App 风格的分组设置列表。
 * 卡片组：待机模式 / 自动化 / 外观与数据 / 系统 / 预览与说明。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val IOS_BLUE = 0xFF0A84FF.toInt()
        private const val IOS_GREEN = 0xFF34C759.toInt()
        private const val CHEVRON_GRAY = 0xFF8E8E93.toInt()
        private const val SEPARATOR = 0xFF3A3A3C.toInt()
    }

    // 动态副标题（onResume 时刷新）
    private var styleSubtitle: TextView? = null
    private var weatherSubtitle: TextView? = null
    private var permSubtitle: TextView? = null
    private var diagSubtitle: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, 0, 0, dp(48))
        scroll.addView(
            content,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
        setContentView(scroll)

        buildHeader(content)

        // 组 1：待机模式
        addSectionHeader(content, "待机模式")
        val card1 = newCard()
        card1.addView(
            switchRow("启用待机模式", Prefs.enabled) { v ->
                Prefs.enabled = v
                if (v) {
                    try {
                        StandbyService.start(this)
                    } catch (e: Exception) {
                        toast("服务启动失败，请检查自启动与后台权限")
                    }
                } else {
                    StandbyService.suppressUntilUnplug = false
                    StandbyService.clockShowing = false
                    StandbyService.stop(this)
                }
            },
            rowLp()
        )
        addCard(content, card1)

        // 组 2：自动化
        addSectionHeader(content, "自动化")
        val card2 = newCard()
        card2.addView(switchRow("开机自启动", Prefs.bootStart) { Prefs.bootStart = it }, rowLp())
        addSeparator(card2)
        card2.addView(switchRow("拔充电线自动退出", Prefs.exitOnUnplug) { Prefs.exitOnUnplug = it }, rowLp())
        addSeparator(card2)
        card2.addView(switchRow("转回竖屏自动退出", Prefs.exitOnPortrait) { Prefs.exitOnPortrait = it }, rowLp())
        addSeparator(card2)
        card2.addView(switchRow("悬浮窗备用模式", Prefs.overlayFallback) { Prefs.overlayFallback = it }, rowLp())
        addCard(content, card2)

        // 组 3：外观 / 防烧屏 / 天气（无组标题）
        styleSubtitle = subtitleTv(fontColorSummary())
        weatherSubtitle = subtitleTv(Prefs.cityName ?: "自动定位")
        val card3 = newCard()
        card3.addView(
            navRow("外观与亮度", styleSubtitle!!, true, StandbyColors.WHITE) { openDetail("style") },
            rowLp()
        )
        addSeparator(card3)
        card3.addView(navRow("防烧屏", null, true, StandbyColors.WHITE) { openDetail("burnin") }, rowLp())
        addSeparator(card3)
        card3.addView(
            navRow("天气与定位", weatherSubtitle!!, true, StandbyColors.WHITE) { openDetail("weather") },
            rowLp()
        )
        addCard(content, card3)

        // 组 4：系统
        addSectionHeader(content, "系统")
        permSubtitle = subtitleTv("")
        diagSubtitle = subtitleTv("")
        val card4 = newCard()
        card4.addView(
            navRow("权限与澎湃OS适配", permSubtitle!!, true, StandbyColors.WHITE) { openDetail("permissions") },
            rowLp()
        )
        addSeparator(card4)
        card4.addView(
            navRow("诊断信息", diagSubtitle!!, true, StandbyColors.WHITE) { openDetail("diagnostics") },
            rowLp()
        )
        addCard(content, card4)

        // 组 5：预览 / 使用说明（无组标题）
        val card5 = newCard()
        card5.addView(
            navRow("立即预览待机时钟", null, true, IOS_BLUE) {
                val i = Intent(this, ClockActivity::class.java)
                i.putExtra(ClockActivity.EXTRA_PREVIEW, true)
                startActivity(i)
            },
            rowLp()
        )
        addSeparator(card5)
        card5.addView(navRow("使用说明", null, true, StandbyColors.WHITE) { openDetail("guide") }, rowLp())
        addCard(content, card5)

        // 页脚
        val footer = TextView(this)
        footer.text = "待机时钟 v1.4.0 · 仿 iOS StandBy · 仅限个人使用"
        footer.setTextColor(StandbyColors.TERTIARY)
        footer.textSize = 13f
        footer.gravity = Gravity.CENTER
        val flp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        flp.topMargin = dp(4)
        content.addView(footer, flp)

        refreshSubtitles()

        // 冗余保障：待机模式启用时确保监控服务在跑
        if (Prefs.enabled) {
            try {
                StandbyService.start(this)
            } catch (e: Exception) {
                // 后台受限时静默失败
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSubtitles()
    }

    /** 刷新所有动态副标题（权限缺失数、服务状态、字体色值、城市名） */
    private fun refreshSubtitles() {
        styleSubtitle?.text = fontColorSummary()
        weatherSubtitle?.text = Prefs.cityName ?: "自动定位"
        val missing = countMissingPermissions()
        permSubtitle?.let { tv ->
            if (missing == 0) {
                tv.text = "全部已开启"
                tv.setTextColor(StandbyColors.TERTIARY)
            } else {
                tv.text = "$missing 项未开启"
                tv.setTextColor(StandbyColors.RED)
            }
        }
        diagSubtitle?.text = if (StandbyService.serviceRunning) "监控运行中" else "监控未运行"
    }

    private fun fontColorSummary(): String =
        String.format(Locale.US, "#%06X", Prefs.fontColor and 0xFFFFFF)

    /** 统计关键权限缺失项数量（与子页权限列表一致；可选与未知状态不计入） */
    private fun countMissingPermissions(): Int {
        var n = 0
        if (!PermissionChecker.overlay(this)) n++
        if (RomInfo.isXiaomi) {
            if (PermissionChecker.backgroundPopup(this) == PermissionChecker.Status.DENIED) n++
            if (PermissionChecker.autoStart(this) == PermissionChecker.Status.DENIED) n++
        }
        if (!PermissionChecker.batteryOptimization(this)) n++
        if (!PermissionChecker.notifications(this)) n++
        if (PermissionChecker.calendar(this) == PermissionChecker.Status.DENIED) n++
        if (PermissionChecker.location(this) == PermissionChecker.Status.DENIED) n++
        return n
    }

    private fun openDetail(section: String) {
        startActivity(
            Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_SECTION, section)
        )
    }

    // ================= 布局构建 =================

    private fun buildHeader(content: LinearLayout) {
        val title = TextView(this)
        title.text = "待机时钟"
        title.setTextColor(StandbyColors.WHITE)
        title.textSize = 34f
        title.typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.BOLD)
        val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tlp.leftMargin = dp(20)
        tlp.topMargin = dp(24)
        content.addView(title, tlp)

        val sub = TextView(this)
        sub.text = "设备：${RomInfo.displayName}"
        sub.setTextColor(StandbyColors.TERTIARY)
        sub.textSize = 13f
        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        slp.leftMargin = dp(20)
        slp.topMargin = dp(2)
        slp.bottomMargin = dp(8)
        content.addView(sub, slp)
    }

    /** iOS 风格分组小标题：13sp TERTIARY 大写、字距 0.08、左边距 32dp、组上方 18dp */
    private fun addSectionHeader(content: LinearLayout, title: String) {
        if (title.isEmpty()) return
        val tv = TextView(this)
        tv.text = title.uppercase()
        tv.setTextColor(StandbyColors.TERTIARY)
        tv.textSize = 13f
        tv.typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
        tv.letterSpacing = 0.08f
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = dp(32)
        lp.topMargin = dp(18)
        lp.bottomMargin = dp(8)
        content.addView(tv, lp)
    }

    /** 卡片：圆角 14dp、填充 #1C1C1E */
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

    /** 卡片加入主列表：水平 margin 16dp、组间距 20dp */
    private fun addCard(content: LinearLayout, card: LinearLayout) {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = dp(16)
        lp.rightMargin = dp(16)
        lp.bottomMargin = dp(20)
        content.addView(card, lp)
    }

    private fun rowLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))

    /** 行内分隔线：0.5dp #3A3A3C，左侧缩进 16dp */
    private fun addSeparator(card: LinearLayout) {
        val v = View(this)
        v.setBackgroundColor(SEPARATOR)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sepHeight())
        lp.leftMargin = dp(16)
        card.addView(v, lp)
    }

    private fun switchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(16), 0)
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(StandbyColors.WHITE)
        tv.textSize = 16f
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(this)
        styleSwitch(sw)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun navRow(
        title: String,
        subtitle: TextView?,
        chevron: Boolean,
        textColor: Int,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(12), 0)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(textColor)
        tv.textSize = 16f
        col.addView(tv)
        if (subtitle != null) col.addView(subtitle)
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (chevron) {
            val ch = TextView(this)
            ch.text = "›"
            ch.setTextColor(CHEVRON_GRAY)
            ch.textSize = 20f
            ch.gravity = Gravity.CENTER
            row.addView(ch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        row.setOnClickListener { onClick() }
        return row
    }

    private fun subtitleTv(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(StandbyColors.TERTIARY)
        tv.textSize = 12f
        return tv
    }

    /** iOS 绿开关：白色滑钮 + 绿色轨道（ColorStateList 染色，两种状态） */
    private fun styleSwitch(sw: SwitchCompat) {
        sw.setTextColor(StandbyColors.WHITE)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(StandbyColors.WHITE, CHEVRON_GRAY))
        sw.trackTintList = ColorStateList(states, intArrayOf(IOS_GREEN, 0xFF39393D.toInt()))
    }

    // ================= 小工具 =================

    private fun sepHeight(): Int =
        ((0.5f * resources.displayMetrics.density) + 0.5f).toInt().coerceAtLeast(1)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
