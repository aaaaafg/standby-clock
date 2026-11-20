package com.deskclock.standby

/**
 * iOS StandBy 风格的配色与字体规范。
 * 全部页面共用，保证观感一致。
 */
object StandbyColors {
    /** 纯黑背景 */
    val BLACK = 0xFF000000.toInt()

    /** 主文字：白色 */
    val WHITE = 0xFFFFFFFF.toInt()

    /** 次级文字：iOS 系统灰 */
    val SECONDARY = 0xFF9BA1A6.toInt()

    /** 三级文字：iOS 深灰 */
    val TERTIARY = 0xFF6E6E73.toInt()

    /** iOS 红色（今天标记、强调） */
    val RED = 0xFFFF3B30.toInt()

    /** 卡片底 */
    val CARD_BG = 0xFF1C1C1E.toInt()

    /** 翻页指示点：激活 */
    val DOT_ACTIVE = 0xFFFFFFFF.toInt()

    /** 翻页指示点：未激活（半透明白） */
    val DOT_INACTIVE = 0x33FFFFFF

    /** 用户设置的字体颜色（默认白色） */
    fun fontColor(): Int = Prefs.fontColor

    /** 大数字时钟使用的无衬线细体 */
    const val TYPEFACE_LIGHT = "sans-serif-light"

    /** 常规无衬线 */
    const val TYPEFACE_REGULAR = "sans-serif"

    /** 中等字重无衬线（标题、日期） */
    const val TYPEFACE_MEDIUM = "sans-serif-medium"
}
