package at.posselt.pfrpg2e.kingdom

import com.foundryvtt.core.helpers.SimpleCalendar
import com.foundryvtt.core.helpers.SimpleCalendarDate
import kotlinx.coroutines.await

suspend fun logToCalendar(title: String, content: String, date: SimpleCalendarDate? = null) {
    val api = SimpleCalendar?.api ?: return
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
