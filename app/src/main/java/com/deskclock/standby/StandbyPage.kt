package com.deskclock.standby

import android.view.View

/**
 * 待机时钟的一页（时钟页 / 日历页 / 天气页）。
 * 由 StandbyPagerView 通过 SwipePagerView 承载，左右滑动切换。
 */
interface StandbyPage {
    /** 每秒调用一次：更新时间、日期变化检测等。 */
    fun onTick()

    /** 页面滑动为可见时调用：刷新数据。 */
    fun onPageShown()

    /** 返回该页的根 View。 */
    fun view(): View
}
