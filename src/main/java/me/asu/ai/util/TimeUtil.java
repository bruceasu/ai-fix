package me.asu.ai.util;

import java.time.LocalDate;
import java.time.ZoneId;

public final class TimeUtil {
    private TimeUtil() {}

    public static long startOfDayMs(LocalDate date, ZoneId zoneId) {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    public static long endExclusiveDayMs(LocalDate date, ZoneId zoneId) {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
    }
}
