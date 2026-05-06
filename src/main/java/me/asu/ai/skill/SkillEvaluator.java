package me.asu.ai.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillEvaluator {
    public SkillExecutionResult evaluate(SkillPlan plan, Map<String, Object> context) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!plan.steps().isEmpty()) {
            String lastKey = outputKey(plan.steps().get(plan.steps().size() - 1));
            result.put("lastStepKey", lastKey);
            result.put("lastStepOutput", context.getOrDefault(lastKey, ""));
        }

        persistYouTubeSummaryIfNeeded(plan.skill().name, plan.input(), context, result);
        persistPlaywrightSmokeReportIfNeeded(plan.skill().name, plan.input(), context, result);

        result.put("stepCount", plan.steps().size());
        result.put("availableKeys", context.keySet());
        return SkillExecutionResult.success(
                plan.skill().name,
                context,
                result,
                "Skill completed with " + plan.steps().size() + " step(s).");
    }

    static void persistYouTubeSummaryIfNeeded(
            String skillName,
            Map<String, Object> input,
            Map<String, Object> context,
            Map<String, Object> result) throws Exception {
        if (!"youtube-transcript-and-summarize".equals(skillName)) {
            return;
        }
        Object summaryObject = context.get("summary");
        if (!(summaryObject instanceof String summaryText) || summaryText.isBlank()) {
            return;
        }

        Path outputPath = determineYoutubeSummaryOutputPath(input, context);
        if (outputPath == null) {
            return;
        }
        Files.createDirectories(outputPath.toAbsolutePath().normalize().getParent());
        Files.writeString(outputPath, summaryText, StandardCharsets.UTF_8);
        result.put("summaryFile", outputPath.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }

    static void persistPlaywrightSmokeReportIfNeeded(
            String skillName,
            Map<String, Object> input,
            Map<String, Object> context,
            Map<String, Object> result) throws Exception {
        if (!"playwright-ui-smoke-test".equals(skillName)) {
            return;
        }
        Object reportObject = context.get("report");
        if (!(reportObject instanceof String reportText) || reportText.isBlank()) {
            return;
        }
        Path outputPath = determinePlaywrightReportOutputPath(input, context);
        if (outputPath == null) {
            return;
        }
        Files.createDirectories(outputPath.toAbsolutePath().normalize().getParent());
        Files.writeString(outputPath, reportText, StandardCharsets.UTF_8);
        result.put("reportFile", outputPath.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }

    private String outputKey(SkillStepDefinition step) {
        return step.outputKey == null || step.outputKey.isBlank() ? step.id : step.outputKey;
    }

    private static Path determineYoutubeSummaryOutputPath(Map<String, Object> input, Map<String, Object> context) {
        Object explicit = input.get("summaryOutput");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return Path.of(String.valueOf(explicit));
        }

        Object transcriptSummary = context.get("transcriptSummary");
        if (!(transcriptSummary instanceof String transcriptJson) || transcriptJson.isBlank()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> summary = mapper.readValue(
                    transcriptJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            String outputDir = stringValue(summary.get("outputDir"));
            List<String> videoIds = extractVideoIds(summary.get("videoIds"));
            if (outputDir == null || outputDir.isBlank()) {
                return null;
            }
            if (!videoIds.isEmpty()) {
                return Path.of(outputDir).resolve(videoIds.get(0) + ".summary.md");
            }
            return Path.of(outputDir).resolve("youtube-summary.md");
        } catch (Exception e) {
            return null;
        }
    }

    private static Path determinePlaywrightReportOutputPath(Map<String, Object> input, Map<String, Object> context) {
        Object explicit = input.get("reportOutput");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return Path.of(String.valueOf(explicit));
        }

        Object screenshotResult = context.get("screenshotResult");
        if (screenshotResult instanceof String screenshotJson && !screenshotJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                Map<String, Object> summary = mapper.readValue(
                        screenshotJson,
                        new TypeReference<Map<String, Object>>() {
                        });
                String stdout = stringValue(summary.get("stdout"));
                if (stdout != null && !stdout.isBlank()) {
                    Path screenshotPath = tryExtractPathFromText(stdout);
                    if (screenshotPath != null) {
                        Path parent = screenshotPath.toAbsolutePath().normalize().getParent();
                        String baseName = fileBaseName(screenshotPath.getFileName().toString());
                        return parent.resolve(baseName + ".report.md");
                    }
                }
            } catch (Exception e) {
                // fall through to session-based default
            }
        }

        Object session = input.get("session");
        String sessionName = session == null || String.valueOf(session).isBlank()
                ? "default"
                : String.valueOf(session);
        return Path.of("workspace", "tools", "playwright-cli", "output", sessionName + ".report.md")
                .toAbsolutePath()
                .normalize();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Path tryExtractPathFromText(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.endsWith(".png") || trimmed.endsWith(".jpg") || trimmed.endsWith(".jpeg")) {
                return Path.of(trimmed);
            }
        }
        return null;
    }

    private static String fileBaseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static List<String> extractVideoIds(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }
}
