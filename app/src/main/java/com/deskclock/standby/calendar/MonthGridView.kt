package com.deskclock.standby.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.deskclock.standby.StandbyColors
import java.time.LocalDate
import java.time.YearMonth

/**
 * 自绘月历网格（周日开头，6 行 × 7 列）。
 *
 * - 今天：红色实心圆 + 白色数字（iOS StandBy 风格）
 * - 选中日：白色实心圆 + 黑色数字
 * - 其余日期：白色数字（跟随用户字体颜色 [StandbyColors.fontColor]）
 *
 * 高度在 [onMeasure] 中按宽度计算：height = width / 7 × 6。
 * 点按当月日期格子时回调 [onDaySelected]；今天默认选中。
 */
class MonthGridView(context: Context) : View(context) {

    /** 点中某个当月日期格子时回调。 */
    var onDaySelected: ((LocalDate) -> Unit)? = null

    /** 当前显示的月份。 */
    private var yearMonth: YearMonth = YearMonth.now()

    /** 当前选中日期（初始为今天）。 */
    private var selectedDate: LocalDate = LocalDate.now()

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(StandbyColors.TYPEFACE_REGULAR, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics
        )
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
    }

    /** 设置要显示的月份并重绘。 */
    fun setMonth(month: YearMonth) {
        if (yearMonth != month) {
            yearMonth = month
            invalidate()
        }
    }

    /** 设置选中日期并重绘。 */
    fun setSelected(date: LocalDate) {
        if (selectedDate != date) {
            selectedDate = date
            invalidate()
        }
    }

    /** 高度按 6 行计算：height = width / 7 × 6；父容器可用高度不足时收缩防溢出。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val byWidth = width * 6 / 7
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            minOf(byWidth, MeasureSpec.getSize(heightMeasureSpec))
        } else {
            byWidth
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val cellWidth = width / 7f
        val cellHeight = height / 6f
        val radius = cellWidth * 0.36f // 直径 ≈ 格宽 × 0.72
        val today = LocalDate.now()
        val firstOffset = yearMonth.atDay(1).dayOfWeek.value % 7 // 周日 = 0
        val daysInMonth = yearMonth.lengthOfMonth()

        // 文字垂直居中：baseline = cy - (ascent + descent) / 2
        val fm = numberPaint.fontMetrics
        val textBaselineOffset = (fm.ascent + fm.descent) / 2f

        for (i in 0 until daysInMonth) {
            val date = yearMonth.atDay(i + 1)
            val cellIndex = firstOffset + i
            val col = cellIndex % 7
            val row = cellIndex / 7
            val cx = (col + 0.5f) * cellWidth
            val cy = (row + 0.5f) * cellHeight

            when {
                date == today -> {
                    circlePaint.color = StandbyColors.RED
                    canvas.drawCircle(cx, cy, radius, circlePaint)
                    numberPaint.color = StandbyColors.WHITE
                }
                date == selectedDate -> {
                    circlePaint.color = StandbyColors.WHITE
                    canvas.drawCircle(cx, cy, radius, circlePaint)
                    numberPaint.color = StandbyColors.BLACK
                }
                else -> numberPaint.color = StandbyColors.fontColor()
            }
            canvas.drawText(date.dayOfMonth.toString(), cx, cy - textBaselineOffset, numberPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            if (x >= 0f && y >= 0f && x < width && y < height) {
                val cellWidth = width / 7f
                val cellHeight = height / 6f
                val col = (x / cellWidth).toInt()
                val row = (y / cellHeight).toInt()
                val firstOffset = yearMonth.atDay(1).dayOfWeek.value % 7
                val dayIndex = row * 7 + col - firstOffset
                if (dayIndex >= 0 && dayIndex < yearMonth.lengthOfMonth()) {
                    val date = yearMonth.atDay(dayIndex + 1)
                    setSelected(date)
                    performClick()
                    onDaySelected?.invoke(date)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
