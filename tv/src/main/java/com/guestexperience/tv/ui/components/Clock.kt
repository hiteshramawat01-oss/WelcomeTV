package com.guestexperience.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.guestexperience.shared.model.TimeFormat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Current time, updated once per minute on the minute: the guest screen recomposes 1×/min, not 1×/s. */
@Composable
fun rememberMinuteClock(now: () -> LocalDateTime = LocalDateTime::now): State<LocalDateTime> =
    produceState(initialValue = now()) {
        while (true) {
            val current = now()
            value = current
            val msIntoMinute = current.second * 1000L + current.nano / 1_000_000L
            delay((60_000L - msIntoMinute).coerceAtLeast(1_000L))
        }
    }

object TimeText {
    private val h12 = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
    private val ampm = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)
    private val h24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val dayDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** ("7:42", "PM") or ("19:42", null). */
    fun clock(time: LocalTime, format: TimeFormat): Pair<String, String?> = when (format) {
        TimeFormat.H12 -> time.format(h12) to time.format(ampm).uppercase(Locale.ENGLISH)
        TimeFormat.H24 -> time.format(h24) to null
    }

    fun clockString(time: LocalTime, format: TimeFormat): String =
        clock(time, format).let { (t, suffix) -> if (suffix == null) t else "$t $suffix" }

    fun day(date: LocalDate): String = date.format(dayDate)
}
