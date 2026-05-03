package me.asu.ai.analyze;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AnalyzePreprocessor {

    private static final int DEFAULT_TAIL_LINES = 300;

    private AnalyzePreprocessor() {
    }

    public static String process(String input, AnalyzeInputType type) {
        return process(input, type, DEFAULT_TAIL_LINES);
    }

    static String process(String input, AnalyzeInputType type, int tailLines) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String[] lines = input.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> tail = tail(lines, tailLines);
        List<String> important = importantLines(lines, type);
        List<String> merged = mergeWithoutDuplicates(important, tail);
        List<String> cleaned = removeNoise(merged);
        return String.join("\n", cleaned).trim();
    }

    private static List<String> importantLines(String[] lines, AnalyzeInputType type) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (isNoise(line)) {
                continue;
            }
            if (isImportant(line, type)) {
                result.add(line);
            }
        }
        return result;
    }

    private static boolean isImportant(String line, AnalyzeInputType type) {
        return switch (type) {
            case JAVA_EXCEPTION -> line.contains("Exception")
                    || line.contains("Error")
                    || line.stripLeading().startsWith("at ");
            case TEST_OUTPUT -> line.contains("FAILURES")
                    || line.contains("Tests run:")
                    || line.contains("AssertionError")
                    || line.contains("expected:")
                    || line.contains("but was:");
            case LOG, UNKNOWN -> line.contains("ERROR")
                    || line.contains("Exception")
                    || line.contains("Error")
                    || line.contains("WARN");
        };
    }

    private static List<String> removeNoise(List<String> lines) {
        List<String> result = new ArrayList<>();
        Set<String> seenStackLines = new LinkedHashSet<>();
        for (String line : lines) {
            if (isNoise(line)) {
                continue;
            }
            if (line.stripLeading().startsWith("at ")) {
                if (!seenStackLines.add(line.trim())) {
                    continue;
                }
            }
            result.add(line);
        }
        return result;
    }

    private static boolean isNoise(String line) {
        return line.contains(" DEBUG ")
                || line.startsWith("DEBUG ")
                || line.contains("[DEBUG]");
    }

    private static List<String> mergeWithoutDuplicates(List<String> primary, List<String> secondary) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.addAll(primary);
        ordered.addAll(secondary);
        return new ArrayList<>(ordered);
    }

    private static List<String> tail(String[] lines, int tailLines) {
        int start = Math.max(0, lines.length - tailLines);
        List<String> result = new ArrayList<>();
        for (int i = start; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isBlank()) {
                result.add(lines[i]);
            }
        }
        return result;
    }
}
