package com.deskclock.standby

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.deskclock.standby.calendar.CalendarPageView
import com.deskclock.standby.clock.ClockPageView
import com.deskclock.standby.weather.WeatherPageView

/**
 * 待机时钟根容器：自研横向滑动翻页（三页：时钟 / 日历 / 天气）+ iOS 风格指示点。
 * 每个页面独立构建：某一页异常时只降级该页，不影响时钟整体。
 */
class StandbyPagerView(context: Context) : FrameLayout(context) {

    private val appContext = context
    private val pages: List<StandbyPage> = buildPages(appContext)
    private val pager: SwipePagerView
    private val dots: PageDotsView

    init {
        pager = SwipePagerView(context)
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pager.setPages(pages.map { it.view() })

        dots = PageDotsView(context)
        val dlp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        dlp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        dlp.bottomMargin = dp(34)
        addView(dots, dlp)

        pager.onPageSelected = { position ->
            dots.setPage(position)
            runCatching { pages[position].onPageShown() }
        }
    }

    /** 每秒刷新：当前可见页更新时间等。 */
    fun onTick() {
        val i = pager.currentPage
        if (i in pages.indices) {
            runCatching { pages[i].onTick() }
        }
    }

    /** 防烧屏像素偏移作用的容器。 */
    fun shiftableView(): View = this

    private fun buildPages(context: Context): List<StandbyPage> = listOf(
        safePage("clock") { ClockPageView(context) },
        safePage("calendar") { CalendarPageView(context) },
        safePage("weather") { WeatherPageView(context) }
    )

    /** 页面构建失败时用占位页降级，并把异常写入日志，绝不拖垮时钟。 */
    private fun safePage(tag: String, factory: () -> StandbyPage): StandbyPage {
        return try {
            factory()
        } catch (t: Throwable) {
            CrashLog.record("page:$tag", t)
            FallbackPage(context, "页面加载失败\n$tag")
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    /** 占位页：黑底灰字，保持整体可用。 */
    private class FallbackPage(context: Context, message: String) : FrameLayout(context), StandbyPage {
        init {
            val tv = TextView(context)
            tv.text = message
            tv.setTextColor(StandbyColors.TERTIARY)
            tv.textSize = 14f
            tv.gravity = Gravity.CENTER
            addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        override fun onTick() {}
        override fun onPageShown() {}
        override fun view(): View = this
    }
}
