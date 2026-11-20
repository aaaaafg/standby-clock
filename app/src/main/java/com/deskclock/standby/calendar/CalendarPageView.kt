package com.deskclock.standby.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.deskclock.standby.StandbyColors
import com.deskclock.standby.StandbyPage
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日历任务页：iOS StandBy 风格的月历 + 当日日程列表。
 *
 * 布局自上而下：
 * 1. 小字行 "今天 · 11月15日 星期六"（13sp TERTIARY）
 * 2. 标题行 "‹ 2026年11月 ›"（22sp TYPEFACE_MEDIUM，‹› 点按区域 ≥44dp）
 * 3. 星期表头 日 一 二 三 四 五 六（13sp TERTIARY，7 等分）
 * 4. [MonthGridView] 月历网格
 * 5. 选中日日程列表（最多 4 条 + "还有 N 项"）
 *
 * 页面背景透明；所有 UI 由代码构建。
 */
class CalendarPageView(context: Context) : FrameLayout(context), StandbyPage {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var todayLabel: TextView
    private lateinit var monthTitle: TextView
    private lateinit var grid: MonthGridView
    private lateinit var eventsContainer: LinearLayout

    /** 当前显示的月份（用户可通过 ‹ › 切换）。 */
    private var currentMonth: YearMonth = YearMonth.now()

    /** 当前选中的日期（今天默认选中）。 */
    private var selectedDate: LocalDate = LocalDate.now()

    /** 上次 onTick 的日期，用于跨天检测。 */
    private var lastDate: LocalDate? = null

    init {
        buildUi()
        refresh()
    }

    // ---------- StandbyPage 接口 ----------

    override fun onTick() {
        val today = LocalDate.now()
        if (today != lastDate) {
            val oldToday = lastDate ?: today
            // 跨天：跟随"今天"的选中与月份（用户手动切走的月份不动）
            if (selectedDate == oldToday) selectedDate = today
            if (currentMonth == YearMonth.from(oldToday)) currentMonth = YearMonth.from(today)
            refresh()
        }
    }

    override fun onPageShown() {
        refresh()
    }

    override fun view(): View = this

    // ---------- 界面构建 ----------

    private fun buildUi() {
        // 横屏两栏布局（与 iOS StandBy 日历页一致）：左侧月历，右侧当日日程
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(44))
        }
        addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // ---- 左栏：月历 ----
        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            leftCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f)
        )

        // 小字行：今天 · 11月15日 星期六
        todayLabel = TextView(context).apply {
            typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
            textSize = 13f
            setTextColor(StandbyColors.TERTIARY)
            gravity = Gravity.CENTER
        }
        leftCol.addView(
            todayLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 标题行：‹ 2026年11月 ›
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prev = arrowTextView("‹") { prevMonth() }
        val next = arrowTextView("›") { nextMonth() }
        monthTitle = TextView(context).apply {
            typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
            textSize = 22f
            setTextColor(StandbyColors.WHITE)
            gravity = Gravity.CENTER
        }
        titleRow.addView(
            prev,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        titleRow.addView(
            monthTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(
            next,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        leftCol.addView(
            titleRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        )

        // 星期表头：日 一 二 三 四 五 六
        val weekRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (name in WEEK_NAMES) {
            val cell = TextView(context).apply {
                text = name
                textSize = 13f
                setTextColor(StandbyColors.TERTIARY)
                gravity = Gravity.CENTER
            }
            weekRow.addView(
                cell,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        leftCol.addView(
            weekRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        // 月历网格（高度不足时自动收缩）
        grid = MonthGridView(context)
        grid.onDaySelected = { date -> onDatePicked(date) }
        leftCol.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        // ---- 右栏：当日日程 ----
        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val rightLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        rightLp.leftMargin = dp(24)
        root.addView(rightCol, rightLp)

        val eventsTitle = TextView(context).apply {
            text = "当日日程"
            typeface = Typeface.create(StandbyColors.TYPEFACE_MEDIUM, Typeface.NORMAL)
            textSize = 15f
            setTextColor(StandbyColors.SECONDARY)
            gravity = Gravity.CENTER
        }
        rightCol.addView(
            eventsTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 日程列表容器
        eventsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        rightCol.addView(
            eventsContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )
    }

    /** ‹ › 切换按钮：白色 20sp，最小点按区域 44dp。 */
    private fun arrowTextView(char: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = char
            textSize = 20f
            setTextColor(StandbyColors.WHITE)
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
            setPadding(dp(10), 0, dp(10), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    // ---------- 数据与刷新 ----------

    /** 整体刷新：日期行、月份标题、网格、日程列表。 */
    private fun refresh() {
        val today = LocalDate.now()
        lastDate = today
        todayLabel.text = "今天 · ${today.monthValue}月${today.dayOfMonth}日 ${weekdayName(today.dayOfWeek)}"
        monthTitle.text = monthTitleText()
        grid.setMonth(currentMonth)
        grid.setSelected(selectedDate)
        refreshEvents()
    }

    /** 重新读取选中日的日程并重建列表。 */
    private fun refreshEvents() {
        eventsContainer.removeAllViews()
        val events = CalendarReader.eventsFor(context, selectedDate)

        // 未授权：提示 + "去开启"按钮
        if (CalendarReader.permissionDenied) {
            val tip = TextView(context).apply {
                text = "日历权限未开启，无法显示日程"
                textSize = 13f
                setTextColor(StandbyColors.TERTIARY)
                gravity = Gravity.CENTER
            }
            eventsContainer.addView(tip, listParams().apply { topMargin = dp(6) })

            val button = TextView(context).apply {
                text = "去开启"
                textSize = 14f
                setTextColor(StandbyColors.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(8), dp(22), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(9f)
                    setStroke(dp(2), StandbyColors.RED)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { openAppSettings() }
            }
            eventsContainer.addView(
                button,
                listParams().apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(14)
                }
            )
            return
        }

        // 无日程
        if (events.isEmpty()) {
            val empty = TextView(context).apply {
                text = "当天无日程"
                textSize = 13f
                setTextColor(StandbyColors.TERTIARY)
                gravity = Gravity.CENTER
            }
            eventsContainer.addView(empty, listParams().apply { topMargin = dp(6) })
            return
        }

        // 日程行（最多 4 条）
        val isToday = selectedDate == LocalDate.now()
        for (event in events.take(4)) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val time = TextView(context).apply {
                text = if (event.allDay) "全天" else formatTime(event.startMillis)
                textSize = 12f
                setTextColor(if (isToday) StandbyColors.RED else StandbyColors.TERTIARY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val title = TextView(context).apply {
                text = event.title
                textSize = 14f
                setTextColor(StandbyColors.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(
                time,
                LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            row.addView(
                title,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            eventsContainer.addView(row, listParams())
        }

        // 超出提示
        if (events.size > 4) {
            val more = TextView(context).apply {
                text = "还有 ${events.size - 4} 项"
                textSize = 13f
                setTextColor(StandbyColors.TERTIARY)
            }
            eventsContainer.addView(more, listParams().apply { topMargin = dp(2) })
        }
    }

    private fun onDatePicked(date: LocalDate) {
        selectedDate = date
        refreshEvents()
    }

    private fun prevMonth() {
        currentMonth = currentMonth.minusMonths(1)
        monthTitle.text = monthTitleText()
        grid.setMonth(currentMonth)
    }

    private fun nextMonth() {
        currentMonth = currentMonth.plusMonths(1)
        monthTitle.text = monthTitleText()
        grid.setMonth(currentMonth)
    }

    private fun monthTitleText(): String = "${currentMonth.year}年${currentMonth.monthValue}月"

    private fun formatTime(startMillis: Long): String =
        Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

    /** 跳转系统应用详情页（用户可在此开启日历权限）。 */
    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // 个别设备无应用详情页，忽略
        }
    }

    private fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }

    /** 日程列表行的通用 LayoutParams。 */
    private fun listParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dpf(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private val WEEK_NAMES = arrayOf("日", "一", "二", "三", "四", "五", "六")
    }
}
