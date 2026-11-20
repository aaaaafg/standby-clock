package com.deskclock.standby

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** iOS 风格翻页指示点：当前页为拉长的圆角横条。 */
class PageDotsView(context: Context) : View(context) {

    private var page = 0
    private val count = 3
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setPage(p: Int) {
        if (page != p) {
            page = p
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dp = resources.displayMetrics.density
        // 最宽情况：3 个激活横条 + 间隙
        val maxW = (3 * 16 + 2 * 7) * dp
        setMeasuredDimension(maxW.toInt(), (10 * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dp = resources.displayMetrics.density
        val dotW = 6 * dp
        val activeW = 16 * dp
        val h = 6 * dp
        val gap = 7 * dp

        var total = 0f
        for (i in 0 until count) {
            total += if (i == page) activeW else dotW
            if (i < count - 1) total += gap
        }

        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val w = if (i == page) activeW else dotW
            paint.color = if (i == page) StandbyColors.DOT_ACTIVE else StandbyColors.DOT_INACTIVE
            canvas.drawRoundRect(x, cy - h / 2, x + w, cy + h / 2, h / 2, h / 2, paint)
            x += w + gap
        }
    }
}
