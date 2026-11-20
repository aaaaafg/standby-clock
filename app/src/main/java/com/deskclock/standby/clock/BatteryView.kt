package com.deskclock.standby.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.deskclock.standby.StandbyColors

/** iOS 风格电池小图标：圆角外框 + 白色电量 + 闪电。 */
class BatteryView(context: Context) : View(context) {

    var level = 100
    var charging = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(32f).toInt(), dp(15f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = dp(1.5f)
        val r = h * 0.30f

        // 外框（留出右侧正极头）
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = StandbyColors.SECONDARY
        val bodyW = w - dp(4f)
        canvas.drawRoundRect(stroke / 2, stroke / 2, bodyW - stroke / 2, h - stroke / 2, r, r, paint)

        // 正极头
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(bodyW + stroke, h * 0.32f, w - stroke / 2, h * 0.68f, r * 0.5f, r * 0.5f, paint)

        // 电量填充
        val inset = dp(2.6f)
        val fillW = (bodyW - inset * 2) * (level / 100f).coerceIn(0f, 1f)
        if (fillW > dp(1f)) {
            paint.color = StandbyColors.WHITE
            canvas.drawRoundRect(inset, inset, inset + fillW, h - inset, r * 0.6f, r * 0.6f, paint)
        }

        // 充电闪电（灰色，白填充与黑底上都可见）
        if (charging) {
            paint.color = StandbyColors.SECONDARY
            val cx = bodyW / 2f
            val cy = h / 2f
            val path = Path()
            path.moveTo(cx + dp(2.2f), h * 0.20f)
            path.lineTo(cx - dp(2.6f), cy + dp(0.8f))
            path.lineTo(cx - dp(0.3f), cy + dp(0.8f))
            path.lineTo(cx - dp(2.2f), h * 0.80f)
            path.lineTo(cx + dp(2.6f), cy - dp(0.8f))
            path.lineTo(cx + dp(0.3f), cy - dp(0.8f))
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
