package me.asu.ai.util;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Duration Parser - parse duration strings
 * Supports: 1s, 10s, 1m, 5m, 1h, 1d, and ISO-8601 (PT1M, PT5M, PT1H, etc.)
 */
public class DurationParser {

    /**
     * Parse duration string to Duration object
     * @param durationStr duration string (e.g., "1m", "5m", "10s", "PT1M")
     * @return Duration object
     */
    public static Duration parse(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be empty");
        }

        String str = durationStr.trim();

        // Try ISO-8601 format first (PT1M, PT5M, etc.)
        if (str.startsWith("PT") || str.startsWith("P")) {
            try {
                return Duration.parse(str);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid ISO-8601 duration: " + str, e);
            }
        }

        // Parse simple format: 1s, 1m, 1h, 1d
        String lower = str.toLowerCase();

        try {
            if (lower.endsWith("ms")) {
                long value = Long.parseLong(lower.substring(0, lower.length() - 2));
                return Duration.ofMillis(value);
            } else if (lower.endsWith("s")) {
                long value = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Duration.ofSeconds(value);
            } else if (lower.endsWith("m")) {
                long value = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Duration.ofMinutes(value);
            } else if (lower.endsWith("h")) {
                long value = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Duration.ofHours(value);
            } else if (lower.endsWith("d")) {
                long value = Long.parseLong(lower.substring(0, lower.length() - 1));
                return Duration.ofDays(value);
            } else {
                // Assume seconds if no unit
                long value = Long.parseLong(lower);
                return Duration.ofSeconds(value);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration format: " + str + ". Expected: 1s, 1m, 1h, 1d, or ISO-8601 (PT1M)", e);
        }
    }

    /**
     * Format Duration to simple string (for display)
     */
    public static String format(Duration duration) {
        if (duration == null) {
            return "null";
        }

        long seconds = duration.getSeconds();

        if (seconds % 86400 == 0) {
            return (seconds / 86400) + "d";
        } else if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        } else if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        } else {
            return seconds + "s";
        }
    }
}
