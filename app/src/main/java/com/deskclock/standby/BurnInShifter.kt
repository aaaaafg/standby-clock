package com.deskclock.standby

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 防烧屏：内容沿一条缓慢的封闭路径周期性偏移（最大约 6dp），
 * 避免同一像素长时间点亮造成 OLED 烧屏。
 */
class BurnInShifter(private val target: View) {

    private val handler = Handler(Looper.getMainLooper())

    /** 偏移路径（dp），回到原点形成闭环 */
    private val positions = listOf(
        0f to 0f,
        6f to -4f,
        -5f to 3f,
        4f to 5f,
        -6f to -3f,
        3f to -5f,
        -3f to 6f,
        0f to 0f
    )
    private var index = 0

    private val move = object : Runnable {
        override fun run() {
            if (!Prefs.antiBurnIn) {
                reset()
                return
            }
            index = (index + 1) % positions.size
            val (x, y) = positions[index]
            val dp = target.resources.displayMetrics.density
            target.animate()
                .translationX(x * dp)
                .translationY(y * dp)
                .setDuration(25000)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
            handler.postDelayed(this, 60000)
        }
    }

    fun start() {
        if (!Prefs.antiBurnIn) return
        handler.removeCallbacks(move)
        handler.postDelayed(move, 45000)
    }

    fun stop() {
        handler.removeCallbacks(move)
        reset()
    }

    private fun reset() {
        target.animate().cancel()
        target.translationX = 0f
        target.translationY = 0f
    }
}
