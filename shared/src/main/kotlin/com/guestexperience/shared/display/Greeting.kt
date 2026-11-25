package com.guestexperience.shared.display

enum class Greeting {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    companion object {
        fun forHour(hour: Int): Greeting = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}
