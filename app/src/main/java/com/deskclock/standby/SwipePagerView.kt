package com.deskclock.standby

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 极简横向滑动翻页容器（不依赖 ViewPager2 / RecyclerView）。
 * 页面并排排列，通过 translationX 与手势拖动切换，iOS 风格回弹阻尼。
 */
class SwipePagerView(context: Context) : FrameLayout(context) {

    private val pages = mutableListOf<View>()
    private var current = 0
    private var dragStartX = 0f
    private var lastDx = 0f
    private var dragging = false
    private var lastSelected = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tracker = VelocityTracker.obtain()

    var onPageSelected: ((Int) -> Unit)? = null

    val currentPage: Int get() = current

    fun setPages(list: List<View>) {
        removeAllViews()
        pages.clear()
        pages.addAll(list)
        for (v in pages) {
            addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        current = 0
        if (width > 0) {
            applyPositions(false)
            notifySelected()
        }
    }

    fun goToPage(index: Int) {
        if (index !in pages.indices || index == current) return
        current = index
        applyPositions(true)
        notifySelected()
    }

    private fun applyPositions(animated: Boolean) {
        for ((i, v) in pages.withIndex()) {
            val x = (i - current) * width.toFloat()
            if (animated) {
                v.animate().translationX(x).setDuration(240).start()
            } else {
                v.translationX = x
            }
        }
    }

    private fun notifySelected() {
        if (lastSelected != current) {
            lastSelected = current
            onPageSelected?.invoke(current)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (v in pages) {
            v.layout(0, 0, width, height)
        }
        applyPositions(false)
        notifySelected()
    }

    /** 拦截横向拖动（纵向滑动/点按仍交给子视图） */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = ev.y
                dragStartX = ev.x
                lastDx = 0f
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - dragStartX
                val dy = ev.y - lastY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                    dragging = true
                    cancelAnimations()
                    return true
                }
            }
        }
        return false
    }

    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker.clear()
                lastY = event.y
                dragStartX = event.x
                dragging = true
                cancelAnimations()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - dragStartX
                lastDx = dx
                applyDrag(dx)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker.computeCurrentVelocity(1000)
                val vx = tracker.xVelocity
                dragging = false
                settle(lastDx, vx)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyDrag(dx: Float) {
        var offset = dx
        // 边缘阻尼（iOS 手感）
        if (current == 0 && dx > 0) offset = dx * 0.3f
        if (current == pages.size - 1 && dx < 0) offset = dx * 0.3f
        for ((i, v) in pages.withIndex()) {
            v.translationX = (i - current) * width.toFloat() + offset
        }
    }

    private fun settle(dx: Float, vx: Float) {
        var target = current
        if (dx < -width / 4f || vx < -1400f) target = current + 1
        if (dx > width / 4f || vx > 1400f) target = current - 1
        target = target.coerceIn(0, pages.size - 1)
        current = target
        applyPositions(true)
        notifySelected()
    }

    private fun cancelAnimations() {
        for (v in pages) v.animate().cancel()
    }
}
