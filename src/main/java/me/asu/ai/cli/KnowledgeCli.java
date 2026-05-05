package me.asu.ai.cli;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeRecord;
import me.asu.ai.knowledge.ProblemKnowledgeStore;

public class KnowledgeCli {

    public static void main(String[] args) {
        String configPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(configPath);
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);

        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "list" -> listRecords(args, config, store);
            case "search" -> searchRecords(args, config, store);
            case "summarize" -> summarizeRecords(args, config, store);
            default -> printUsage();
        }
    }

    private static void listRecords(String[] args, AppConfig config, ProblemKnowledgeStore store) {
        int limit = parsePositiveInt(findOptionValue(args, "--limit"), 10);
        RecordFilter filter = RecordFilter.fromArgs(args);
        printHeader(config.getKnowledgeDirectory(), "recent", limit, filter);
        printRecords(applyFilter(store.listAllRecords(), filter).stream().limit(limit).toList());
    }

    private static void searchRecords(String[] args, AppConfig config, ProblemKnowledgeStore store) {
        String query = findOptionValue(args, "--query");
        if (query == null || query.isBlank()) {
            System.out.println("Missing required option: --query");
            return;
        }
        int limit = parsePositiveInt(findOptionValue(args, "--limit"), 10);
        RecordFilter filter = RecordFilter.fromArgs(args);
        printHeader(config.getKnowledgeDirectory(), "search: " + query, limit, filter);
        printRecords(applyFilter(store.searchRecords(query, Math.max(limit * 5, limit)), filter).stream()
                .limit(limit)
                .toList());
    }

    private static void summarizeRecords(String[] args, AppConfig config, ProblemKnowledgeStore store) {
        int limit = parsePositiveInt(findOptionValue(args, "--top"), 5);
        RecordFilter filter = RecordFilter.fromArgs(args);
        List<ProblemKnowledgeRecord> records = applyFilter(store.listAllRecords(), filter);
        System.out.println("Knowledge dir: " + config.getKnowledgeDirectory());
        System.out.println("Mode: summarize");
        System.out.println("Top: " + limit);
        printFilterSummary(filter);
        System.out.println();
        System.out.println("Total records: " + records.size());
        if (records.isEmpty()) {
            System.out.println("(no records)");
            return;
        }
        printCountSection("By source", records, ProblemKnowledgeCliFunctions::sourceKey, limit);
        printCountSection("By status", records, ProblemKnowledgeCliFunctions::statusKey, limit);
        printCountSection("By problem type", records, ProblemKnowledgeCliFunctions::problemTypeKey, limit);
        printCountSection("By classification", records, ProblemKnowledgeCliFunctions::classificationKey, limit);
    }

    private static void printHeader(Path knowledgeDir, String mode, int limit, RecordFilter filter) {
        System.out.println("Knowledge dir: " + knowledgeDir);
        System.out.println("Mode: " + mode);
        System.out.println("Limit: " + limit);
        printFilterSummary(filter);
        System.out.println();
    }

    private static void printFilterSummary(RecordFilter filter) {
        if (!filter.isActive()) {
            return;
        }
        if (!filter.source().isBlank()) {
            System.out.println("Filter source: " + filter.source());
        }
        if (!filter.status().isBlank()) {
            System.out.println("Filter status: " + filter.status());
        }
        if (!filter.problemType().isBlank()) {
            System.out.println("Filter type: " + filter.problemType());
        }
    }

    private static void printRecords(List<ProblemKnowledgeRecord> records) {
        if (records.isEmpty()) {
            System.out.println("(no records)");
            return;
        }
        int index = 1;
        for (ProblemKnowledgeRecord record : records) {
            System.out.println(index++ + ". "
                    + safe(record.timestamp)
                    + " | "
                    + safe(record.source)
                    + " | "
                    + safe(record.status)
                    + " | "
                    + firstNonBlank(record.classification, record.problemType, "(uncategorized)"));
            if (!safe(record.task).isBlank()) {
                System.out.println("   task: " + shorten(record.task, 160));
            }
            if (!safe(record.target).isBlank()) {
                System.out.println("   target: " + shorten(record.target, 160));
            }
            if (!safe(record.file).isBlank()) {
                System.out.println("   file: " + shorten(record.file, 160));
            }
            if (!safe(record.repairScheme).isBlank()) {
                System.out.println("   repair: " + shorten(singleLine(record.repairScheme), 220));
            }
            if (!safe(record.analysis).isBlank()) {
                System.out.println("   analysis: " + shorten(singleLine(record.analysis), 220));
            }
            System.out.println();
        }
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar knowledge list [--limit <number>] [--source <name>] [--status <name>] [--type <name>] [--config <path>]
                  java -jar ai-fix-<version>.jar knowledge search --query <text> [--limit <number>] [--source <name>] [--status <name>] [--type <name>] [--config <path>]
                  java -jar ai-fix-<version>.jar knowledge summarize [--top <number>] [--source <name>] [--status <name>] [--type <name>] [--config <path>]
                """);
    }

    private static List<ProblemKnowledgeRecord> applyFilter(List<ProblemKnowledgeRecord> records, RecordFilter filter) {
        return records.stream().filter(record -> filter.matches(record)).toList();
    }

    private static void printCountSection(
            String title,
            List<ProblemKnowledgeRecord> records,
            Function<ProblemKnowledgeRecord, String> classifier,
            int top) {
        System.out.println(title + ":");
        Map<String, Long> counts = records.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(0, top))
                .forEach(entry -> System.out.println("  - " + entry.getKey() + ": " + entry.getValue()));
        System.out.println();
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String findOptionValue(String[] args, String optionName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static String singleLine(String text) {
        return safe(text).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String shorten(String text, int max) {
        String value = safe(text);
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max)).trim() + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ProblemKnowledgeCliFunctions {

        private static String sourceKey(ProblemKnowledgeRecord record) {
            return firstNonBlank(record.source, "(unknown)");
        }

        private static String statusKey(ProblemKnowledgeRecord record) {
            return firstNonBlank(record.status, "(unknown)");
        }

        private static String problemTypeKey(ProblemKnowledgeRecord record) {
            return firstNonBlank(record.problemType, "(unknown)");
        }

        private static String classificationKey(ProblemKnowledgeRecord record) {
            return firstNonBlank(record.classification, record.problemType, "(uncategorized)");
        }
    }

    private record RecordFilter(String source, String status, String problemType) {

        static RecordFilter fromArgs(String[] args) {
            return new RecordFilter(
                    normalized(findOptionValue(args, "--source")),
                    normalized(findOptionValue(args, "--status")),
                    normalized(findOptionValue(args, "--type")));
        }

        boolean isActive() {
            return !source.isBlank() || !status.isBlank() || !problemType.isBlank();
        }

        boolean matches(ProblemKnowledgeRecord record) {
            if (!source.isBlank() && !source.equalsIgnoreCase(safe(record.source))) {
                return false;
            }
            if (!status.isBlank() && !status.equalsIgnoreCase(safe(record.status))) {
                return false;
            }
            if (!problemType.isBlank() && !problemType.equalsIgnoreCase(safe(record.problemType))) {
                return false;
            }
            return true;
        }

        private static String normalized(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
