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
import me.asu.ai.config.AppConfig;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;

public class SkillOrchestrator {

    private final SkillCatalogService skillCatalogService;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SkillOrchestrator(
            SkillCatalogService skillCatalogService,
            ToolExecutor toolExecutor,
            AppConfig config) {
        this.skillCatalogService = skillCatalogService;
        this.toolExecutor = toolExecutor;
    }

    public SkillExecutionResult execute(
            String skillName,
            String inputJson,
            boolean confirmed,
            String provider,
            String model) {
        return execute(skillName, inputJson, confirmed, provider, model, null);
    }

    public SkillExecutionResult execute(
            String skillName,
            String inputJson,
            boolean confirmed,
            String provider,
            String model,
            String emitYamlPath) {
        try {
            SkillDefinition skill = resolveSkillDefinition(skillName, provider, model, emitYamlPath);
            Map<String, Object> context = new LinkedHashMap<>();
            Map<String, Object> input = mapper.readValue(
                    inputJson == null || inputJson.isBlank() ? "{}" : inputJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            context.put("input", input);

            for (SkillStepDefinition step : skill.steps) {
                if ("ai".equals(step.type)) {
                    return SkillExecutionResult.failure(
                            skillName,
                            "Legacy ai step is no longer supported. Migrate this skill to toolName=llm.");
                }
                if ("tool".equals(step.type)) {
                    Map<String, String> args = buildToolArguments(step, context, provider, model);
                    ToolExecutionResult result = toolExecutor.execute(
                            step.toolName,
                            mapper.writeValueAsString(args),
                            confirmed || skill.autoExecuteAllowed);
                    if (!result.ok()) {
                        if (step.optional) {
                            context.put(outputKey(step), "");
                            continue;
                        }
                        return SkillExecutionResult.failure(skillName, result.error());
                    }
                    context.put(outputKey(step), result.output());
                    context.put(outputKey(step) + "Data", result.data());
                    continue;
                }
                return SkillExecutionResult.failure(skillName, "Unsupported step type: " + step.type);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            if (!skill.steps.isEmpty()) {
                String lastKey = outputKey(skill.steps.get(skill.steps.size() - 1));
                result.put("lastStepKey", lastKey);
                result.put("lastStepOutput", context.getOrDefault(lastKey, ""));
            }
            persistYouTubeSummaryIfNeeded(skillName, input, context, result);
            persistPlaywrightSmokeReportIfNeeded(skillName, input, context, result);
            result.put("stepCount", skill.steps.size());
            result.put("availableKeys", context.keySet());
            return SkillExecutionResult.success(
                    skillName,
                    context,
                    result,
                    "Skill completed with " + skill.steps.size() + " step(s).");
        } catch (Exception e) {
            return SkillExecutionResult.failure(skillName, e.getMessage());
        }
    }

    private SkillDefinition resolveSkillDefinition(
            String skillName,
            String provider,
            String model,
            String emitYamlPath) throws Exception {
        try {
            return skillCatalogService.getRequired(skillName);
        } catch (IllegalArgumentException ignored) {
            Path markdownPath = skillCatalogService.findMarkdownOnlySkillDocument(skillName)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillName));
            SkillDefinition definition = SkillMarkdownSupport.buildRuntimeSkillDefinition(
                    markdownPath,
                    skillName,
                    toolExecutor,
                    provider,
                    model,
                    true);
            if (emitYamlPath != null && !emitYamlPath.isBlank()) {
                Path outputPath = Path.of(emitYamlPath).toAbsolutePath().normalize();
                if (outputPath.getParent() != null) {
                    Files.createDirectories(outputPath.getParent());
                }
                yamlMapper.writeValue(outputPath.toFile(), SkillMarkdownSupport.toPortableSkillMap(definition));
            }
            return definition;
        }
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

    static Path determineYoutubeSummaryOutputPath(Map<String, Object> input, Map<String, Object> context) {
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

    static Path determinePlaywrightReportOutputPath(Map<String, Object> input, Map<String, Object> context) {
        Object explicit = input.get("reportOutput");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return Path.of(String.valueOf(explicit));
        }

        Object screenshotResult = context.get("screenshotResult");
        if (screenshotResult instanceof String screenshotJson && !screenshotJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
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

    private String outputKey(SkillStepDefinition step) {
        return step.outputKey == null || step.outputKey.isBlank() ? step.id : step.outputKey;
    }

    private Map<String, String> buildToolArguments(
            SkillStepDefinition step,
            Map<String, Object> context,
            String provider,
            String model) {
        Map<String, String> args = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : step.arguments.entrySet()) {
            args.put(entry.getKey(), resolveTemplate(entry.getValue(), context));
        }
        if ("llm".equals(step.toolName)) {
            if (!args.containsKey("provider") || args.get("provider") == null || args.get("provider").isBlank()) {
                String effectiveProvider = step.provider != null && !step.provider.isBlank() ? step.provider : provider;
                if (effectiveProvider != null && !effectiveProvider.isBlank()) {
                    args.put("provider", effectiveProvider);
                }
            }
            if (!args.containsKey("model") || args.get("model") == null || args.get("model").isBlank()) {
                if (model != null && !model.isBlank()) {
                    args.put("model", model);
                }
            }
        }
        return args;
    }

    private String resolveTemplate(String template, Map<String, Object> context) {
        if (template == null) {
            return "";
        }
        String resolved = template;
        Object inputObject = context.get("input");
        if (inputObject instanceof Map<?, ?> input) {
            for (Map.Entry<?, ?> entry : input.entrySet()) {
                resolved = resolved.replace("${input." + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return resolved;
    }
}
