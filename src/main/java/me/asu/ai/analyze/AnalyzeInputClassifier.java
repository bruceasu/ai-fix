package me.asu.ai.analyze;

import java.util.regex.Pattern;

public final class AnalyzeInputClassifier {

    private static final Pattern JAVA_STACK_TRACE_PATTERN = Pattern.compile("(?m)^\\s*at\\s+.+\\.java:\\d+.*$");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            ".*(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}:\\d{2}:\\d{2}|\\d{4}/\\d{2}/\\d{2}).*",
            Pattern.DOTALL);

    private AnalyzeInputClassifier() {
    }

    public static AnalyzeInputType classify(String input) {
        if (input == null || input.isBlank()) {
            return AnalyzeInputType.UNKNOWN;
        }
        if (containsTestSignal(input)) {
            return AnalyzeInputType.TEST_OUTPUT;
        }
        if (containsJavaExceptionSignal(input)) {
            return AnalyzeInputType.JAVA_EXCEPTION;
        }
        if (containsLogSignal(input)) {
            return AnalyzeInputType.LOG;
        }
        return AnalyzeInputType.LOG;
    }

    private static boolean containsJavaExceptionSignal(String input) {
        return input.contains("Exception")
                || input.contains("Error")
                || JAVA_STACK_TRACE_PATTERN.matcher(input).find();
    }

    private static boolean containsTestSignal(String input) {
        return input.contains("FAILURES")
                || input.contains("Tests run:")
                || input.contains("AssertionError");
    }

    private static boolean containsLogSignal(String input) {
        return TIMESTAMP_PATTERN.matcher(input).matches()
                || input.contains(" INFO ")
                || input.contains(" WARN ")
                || input.contains(" ERROR ");
    }
}
