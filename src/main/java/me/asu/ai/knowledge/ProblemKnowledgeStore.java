package me.asu.ai.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.asu.ai.config.AppConfig;

public class ProblemKnowledgeStore {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProblemKnowledgeStore(AppConfig config) {
        this.config = config;
    }

    public void recordToolExecution(String name, boolean success, String output, Map<String, Object> metadata, String error) {
        ProblemKnowledgeRecord record = new ProblemKnowledgeRecord();
        record.timestamp = LocalDateTime.now().format(TS);
        record.source = "tool";
        record.mode = "execute";
        record.status = success ? "success" : "failure";
        record.problemType = "tool-execution";
        record.classification = safe(name);
        record.analysis = success ? truncate(output, 2000) : "";
        record.repairScheme = "";
        record.error = safe(error);
        if (metadata != null) {
            record.metadata.putAll(metadata);
        }
        persist(record);
    }

    public void recordSkillExecution(String name, boolean success, Map<String, Object> context, String error) {
        ProblemKnowledgeRecord record = new ProblemKnowledgeRecord();
        record.timestamp = LocalDateTime.now().format(TS);
        record.source = "skill";
        record.mode = "execute";
        record.status = success ? "success" : "failure";
        record.problemType = "skill-execution";
        record.classification = safe(name);
        try {
            record.analysis = truncate(mapper.writeValueAsString(context == null ? Map.of() : context), 4000);
        } catch (Exception e) {
            record.analysis = "(failed to serialize context)";
        }
        record.error = safe(error);
        persist(record);
    }


    public List<ProblemKnowledgeRecord> listRecentRecords(int limit) {
        List<ProblemKnowledgeRecord> records = loadAllRecords();
        return records.stream()
                .sorted(Comparator.comparing(record -> safe(record.timestamp), Comparator.reverseOrder()))
                .limit(Math.max(0, limit))
                .toList();
    }

    public List<ProblemKnowledgeRecord> listAllRecords() {
        return loadAllRecords().stream()
                .sorted(Comparator.comparing(record -> safe(record.timestamp), Comparator.reverseOrder()))
                .toList();
    }

    public List<ProblemKnowledgeRecord> searchRecords(String query, int limit) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return listRecentRecords(limit);
        }
        List<ScoredRecord> scored = new ArrayList<>();
        for (ProblemKnowledgeRecord record : loadAllRecords()) {
            int score = scoreTextRecord(record, queryTokens);
            if (score > 0) {
                scored.add(new ScoredRecord(record, score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredRecord::score).reversed())
                .limit(Math.max(0, limit))
                .map(ScoredRecord::record)
                .toList();
    }

    public List<ProblemKnowledgeRecord> searchSuccessfulRecords(String query, int limit) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return listAllRecords().stream()
                    .filter(this::isAiReusable)
                    .limit(Math.max(0, limit))
                    .toList();
        }
        List<ScoredRecord> scored = new ArrayList<>();
        for (ProblemKnowledgeRecord record : loadAllRecords()) {
            if (!isAiReusable(record)) {
                continue;
            }
            int score = scoreTextRecord(record, queryTokens);
            if (score > 0) {
                scored.add(new ScoredRecord(record, score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredRecord::score).reversed())
                .limit(Math.max(0, limit))
                .map(ScoredRecord::record)
                .toList();
    }

    private void persist(ProblemKnowledgeRecord record) {
        try {
            Path knowledgeDir = config.getKnowledgeDirectory();
            Path recordsDir = knowledgeDir.resolve("records");
            Files.createDirectories(recordsDir);
            Path jsonl = knowledgeDir.resolve("records.jsonl");
            String jsonLine = mapper.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(
                    jsonl,
                    jsonLine,
                    StandardCharsets.UTF_8,
                    Files.exists(jsonl) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);

            String slug = slugify(firstNonBlank(record.classification, record.problemType, "record"));
            Path markdown = recordsDir.resolve(FILE_TS.format(LocalDateTime.now()) + "-" + slug + ".md");
            Files.writeString(markdown, toMarkdown(record), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Knowledge recording skipped: " + e.getMessage());
        }
    }

    private List<ProblemKnowledgeRecord> loadAllRecords() {
        try {
            Path jsonl = config.getKnowledgeDirectory().resolve("records.jsonl");
            if (!Files.isRegularFile(jsonl)) {
                return List.of();
            }
            List<ProblemKnowledgeRecord> records = new ArrayList<>();
            List<String> lines = Files.readAllLines(jsonl, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                records.add(mapper.readValue(line, ProblemKnowledgeRecord.class));
            }
            return records;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void enrichSuccessfulFixRecord(ProblemKnowledgeRecord record, String patchFile) {
        PatchInsight insight = readPatchInsight(patchFile);
        if (insight == null) {
            return;
        }
        record.analysis = insight.analysis();
        record.repairScheme = insight.repairScheme();
        if (!insight.changedFiles().isEmpty()) {
            record.metadata.put("changedFiles", insight.changedFiles());
        }
        record.metadata.put("addedLines", insight.addedLines());
        record.metadata.put("removedLines", insight.removedLines());
        record.metadata.put("hunkCount", insight.hunkCount());
        record.metadata.put("repairPattern", insight.repairPattern());
    }

    private PatchInsight readPatchInsight(String patchFile) {
        try {
            if (patchFile == null || patchFile.isBlank()) {
                return null;
            }
            Path path = Path.of(patchFile).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return null;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> changedFiles = new ArrayList<>();
            List<String> addedSnippets = new ArrayList<>();
            int addedLines = 0;
            int removedLines = 0;
            int hunkCount = 0;
            for (String line : lines) {
                if (line.startsWith("+++ b/")) {
                    changedFiles.add(line.substring("+++ b/".length()).trim());
                    continue;
                }
                if (line.startsWith("@@")) {
                    hunkCount++;
                    continue;
                }
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    addedLines++;
                    String snippet = line.substring(1).trim();
                    if (!snippet.isBlank() && addedSnippets.size() < 3) {
                        addedSnippets.add(snippet);
                    }
                    continue;
                }
                if (line.startsWith("-") && !line.startsWith("---")) {
                    removedLines++;
                }
            }
            String analysis = "Patch applied successfully. Files="
                    + changedFiles.size()
                    + ", hunks="
                    + hunkCount
                    + ", addedLines="
                    + addedLines
                    + ", removedLines="
                    + removedLines
                    + ".";
            String repairScheme;
            if (addedSnippets.isEmpty()) {
                repairScheme = "Apply the minimal validated patch and preserve surrounding behavior compatibility.";
            } else {
                repairScheme = addedSnippets.stream()
                        .map(snippet -> "- " + snippet)
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
            String repairPattern = classifyRepairPattern(lines, changedFiles, addedSnippets);
            return new PatchInsight(analysis, repairScheme, changedFiles, addedLines, removedLines, hunkCount, repairPattern);
        } catch (Exception e) {
            return null;
        }
    }

    private String classifyRepairPattern(List<String> patchLines, List<String> changedFiles, List<String> addedSnippets) {
        String combined = String.join("\n", patchLines).toLowerCase();
        String addedCombined = String.join("\n", addedSnippets).toLowerCase();
        String changedFilesText = String.join("\n", changedFiles).toLowerCase();

        if (containsAny(addedCombined, "== null", "!= null", "objects.requireNonNull", "optional.ofnullable")
                || containsAny(combined, "nullpointerexception", "null check")) {
            return "null-guard";
        }
        if (containsAny(addedCombined, "catch (", "throw new ", "throws ", "try {")
                || containsAny(combined, "exception", "illegalargumentexception", "illegalstateexception")) {
            return "exception-handling";
        }
        if (containsAny(addedCombined, "isEmpty()", ".size()", "length", "index <", "index >=", "range", "min(", "max(")
                || containsAny(combined, "outofbound", "indexoutofbounds")) {
            return "boundary-check";
        }
        if (containsAny(changedFilesText, "application.", "bootstrap", "config", "properties", "yaml", "yml", "json", "xml")
                || containsAny(addedCombined, "@value(", "@configurationproperties", "system.getenv", "getproperty(")) {
            return "config-fix";
        }
        if (containsAny(addedCombined, ".init(", ".start(", ".open(", ".connect(", ".close(", ".shutdown(")
                || containsAny(combined, "before", "after", "order", "sequence")) {
            return "dependency-call-order";
        }
        if (containsAny(addedCombined, "validate(", "validator", "assert ", "assertion", "precondition")) {
            return "validation-check";
        }
        return "behavior-guard";
    }

    private int scoreTextRecord(ProblemKnowledgeRecord record, Set<String> queryTokens) {
        Set<String> recordTokens = new HashSet<>();
        recordTokens.addAll(tokenize(record.source));
        recordTokens.addAll(tokenize(record.mode));
        recordTokens.addAll(tokenize(record.status));
        recordTokens.addAll(tokenize(record.problemType));
        recordTokens.addAll(tokenize(record.classification));
        recordTokens.addAll(tokenize(record.task));
        recordTokens.addAll(tokenize(record.target));
        recordTokens.addAll(tokenize(record.file));
        recordTokens.addAll(tokenize(record.analysis));
        recordTokens.addAll(tokenize(record.repairScheme));
        int score = 0;
        for (String token : queryTokens) {
            if (recordTokens.contains(token)) {
                score += 2;
            }
        }
        if ("success".equalsIgnoreCase(record.status)) {
            score += 1;
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String normalized = safe(text).toLowerCase().replaceAll("[^a-z0-9]+", " ");
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean matchesIgnoreCase(String left, String right) {
        return !safe(left).isBlank() && !safe(right).isBlank() && safe(left).equalsIgnoreCase(safe(right));
    }

    private boolean isAiReusable(ProblemKnowledgeRecord record) {
        return record != null
                && "fix".equalsIgnoreCase(record.source)
                && "success".equalsIgnoreCase(record.status);
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static String extractSection(String text, String label) {
        if (text == null || text.isBlank() || label == null || label.isBlank()) {
            return "";
        }
        int start = text.indexOf(label);
        if (start < 0) {
            return "";
        }
        String tail = text.substring(start + label.length()).trim();
        int next = tail.indexOf("\n[");
        if (next >= 0) {
            tail = tail.substring(0, next).trim();
        }
        return tail.trim();
    }

    private String toMarkdown(ProblemKnowledgeRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Problem Knowledge Record\n\n");
        appendLine(sb, "Timestamp", record.timestamp);
        appendLine(sb, "Source", record.source);
        appendLine(sb, "Mode", record.mode);
        appendLine(sb, "Status", record.status);
        appendLine(sb, "Problem Type", record.problemType);
        appendLine(sb, "Classification", record.classification);
        appendLine(sb, "Task", record.task);
        appendLine(sb, "Target", record.target);
        appendLine(sb, "File", record.file);
        appendLine(sb, "Line Range", record.lineRange);
        appendBlock(sb, "Input Snippet", record.inputSnippet);
        appendBlock(sb, "Analysis", record.analysis);
        appendBlock(sb, "Repair Scheme", record.repairScheme);
        appendBlock(sb, "Error", record.error);
        if (record.metadata != null && !record.metadata.isEmpty()) {
            sb.append("## Metadata\n\n");
            for (Map.Entry<String, Object> entry : record.metadata.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(String.valueOf(entry.getValue())).append("\n");
            }
        }
        return sb.toString().trim() + "\n";
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("- ").append(label).append(": ").append(value).append("\n");
    }

    private void appendBlock(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("\n## ").append(label).append("\n\n");
        sb.append(value.trim()).append("\n");
    }

    private static String slugify(String text) {
        String normalized = safe(text).toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        return normalized.isBlank() ? "record" : normalized;
    }

    private static String truncate(String text, int max) {
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

    private record ScoredRecord(ProblemKnowledgeRecord record, int score) {
    }

    private record PatchInsight(
            String analysis,
            String repairScheme,
            List<String> changedFiles,
            int addedLines,
            int removedLines,
            int hunkCount,
            String repairPattern) {
    }
}
