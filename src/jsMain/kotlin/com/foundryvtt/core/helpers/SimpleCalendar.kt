package com.foundryvtt.core.helpers

import kotlin.js.Promise

external interface SimpleCalendarDate {
    val year: Int
    val month: Int // 0-indexed (0 = Abadius)
    val day: Int // 1-indexed
    val hour: Int
    val minute: Int
    val second: Int
}

external interface SimpleCalendarApi {
    fun getCurrentDate(): SimpleCalendarDate
    fun addNote(
        title: String,
        content: String,
        startDate: SimpleCalendarDate,
        endDate: SimpleCalendarDate,
        allDay: Boolean,
        repeats: Int,
        categories: Array<String>,
        calendarId: String = definedExternally
    ): Promise<Any?>
}

external interface SimpleCalendarGlobal {
    val api: SimpleCalendarApi?
}

external val SimpleCalendar: SimpleCalendarGlobal?
