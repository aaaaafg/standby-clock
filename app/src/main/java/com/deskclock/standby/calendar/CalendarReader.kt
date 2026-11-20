package com.deskclock.standby.calendar

import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/**
 * 单日日程条目。
 *
 * @param title 事件标题
 * @param startMillis 开始时间（毫秒，本地时区语义）
 * @param allDay 是否全天事件
 */
data class CalendarEvent(val title: String, val startMillis: Long, val allDay: Boolean)

/**
 * 系统日历读取器。
 *
 * 查询 CalendarContract.Events，需要 READ_CALENDAR 权限；
 * 权限被拒或查询异常时返回空列表并置 [permissionDenied]，由页面展示提示。
 */
object CalendarReader {

    /** 当前是否被拒绝日历权限（SecurityException 时置 true）。 */
    var permissionDenied: Boolean = false
        private set

    /**
     * 读取某一天（本地时区）的事件，按开始时间升序。
     * 无权限/异常时返回空列表并置 [permissionDenied]。
     */
    fun eventsFor(context: Context, date: LocalDate): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val zone = ZoneId.systemDefault()
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        permissionDenied = false
        try {
            context.applicationContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.ALL_DAY
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?",
                arrayOf(startMillis.toString(), endMillis.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                if (titleIdx < 0 || startIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIdx) ?: ""
                    val start = cursor.getLong(startIdx)
                    val allDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) != 0
                    events.add(CalendarEvent(title, start, allDay))
                }
            }
        } catch (e: SecurityException) {
            permissionDenied = true
            return emptyList()
        } catch (e: Exception) {
            permissionDenied = true
            return emptyList()
        }
        return events
    }
}
