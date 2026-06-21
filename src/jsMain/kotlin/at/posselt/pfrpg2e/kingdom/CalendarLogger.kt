package at.posselt.pfrpg2e.kingdom

import com.foundryvtt.core.helpers.SimpleCalendarDate
import com.foundryvtt.core.helpers.simpleCalendarOrNull
import kotlinx.coroutines.await

suspend fun logToCalendar(title: String, content: String, date: SimpleCalendarDate? = null) {
    // Gracefully no-op when no Simple Calendar integration is present (Seasons & Stars without the
    // compat bridge, or no calendar module). Touching the bare global would throw a ReferenceError.
    val api = simpleCalendarOrNull()?.api ?: return
    val targetDate = date ?: api.getCurrentDate()
    try {
        api.addNote(
            title = title,
            content = content,
            startDate = targetDate,
            endDate = targetDate,
            allDay = true,
            repeats = 0,
            categories = arrayOf("Kingmaker")
        ).await()
    } catch (e: Exception) {
        console.error("Failed to add calendar note: ", e)
    }
}
