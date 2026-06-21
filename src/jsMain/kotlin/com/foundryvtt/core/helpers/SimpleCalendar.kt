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

/**
 * Safely resolve the optional `SimpleCalendar` global, provided by the Simple Calendar module or
 * the Seasons & Stars compatibility bridge. When neither is installed the global is *undeclared*,
 * so referencing `SimpleCalendar` directly throws a `ReferenceError` that a null-safe `?.` cannot
 * guard against (the reference itself throws before `?.` runs). A `typeof` check is the only safe
 * way to probe a possibly-undeclared global, so callers must use this instead of `SimpleCalendar`.
 */
fun simpleCalendarOrNull(): SimpleCalendarGlobal? =
    js("typeof SimpleCalendar !== 'undefined' ? SimpleCalendar : null").unsafeCast<SimpleCalendarGlobal?>()
