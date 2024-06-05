package com.morpheusdata.uthman.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DateTimeUtils {
    static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)

    // Retrieve the current date and time [optional: formatted as a string]
    public static String getCurrentDateTime(boolean asString = false) {
        LocalDateTime currentDateTime = LocalDateTime.now()
        return asString ? currentDateTime.format(FORMATTER) : currentDateTime.toString()
    }

    // Get a date-time reference based on a specified time interval
    public static String getDateTimeRef(String timeInterval) {
        LocalDateTime currentDateTime = LocalDateTime.now()
        long daysAgo
        switch (timeInterval) {
            case "now-90d/d":
                90
                break
            case "now-60d/d":
                60
                break
            case "now-30d/d":
                30
                break
            case "now":
                0
                break
            default:
                throw new IllegalArgumentException("Invalid time interval: ${timeInterval}")
                break
        }
        LocalDateTime pastDateTime = currentDateTime.minusDays(daysAgo)
        return pastDateTime.format(FORMATTER)
    }
}
