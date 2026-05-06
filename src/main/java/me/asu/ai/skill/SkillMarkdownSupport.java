package me.asu.ai.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;

public final class SkillMarkdownSupport {

    public static final List<String> DOCUMENT_FILENAMES = List.of("SKILL.md", "README.md");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private SkillMarkdownSupport() {}

    public record ConversionResult(
            boolean aiGenerated,
            boolean fallbackGenerated,
            String skillName,
            Path markdownFile,
            Path yamlFile,
            String note) {}

    public static String extractTitle(String markdown, String fallbackName) {
        if (markdown == null || markdown.isBlank()) {
            return fallbackName;
        }
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String title = trimmed.replaceFirst("^#+\\s*", "").trim();
                if (!title.isBlank()) {
                    return title;
                }
            }
        }
        return fallbackName;
    }

    public static String extractSummary(String markdown, String fallbackDescription) {
        if (markdown == null || markdown.isBlank()) {
            return fallbackDescription;
        }
        StringBuilder summary = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                if (summary.length() > 0) {
                    break;
                }
                continue;
            }
            if (trimmed.startsWith("#")) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(trimmed);
            if (summary.length() >= 180) {
                break;
            }
        }
        if (summary.isEmpty()) {
            return fallbackDescription;
        }
        String text = summary.toString().trim();
        if (text.length() > 180) {
            return text.substring(0, 177).trim() + "...";
        }
        return text;
    }

    public static String inferSkillName(Path markdownPath, Path outputDir, String overrideName) {
        if (overrideName != null && !overrideName.isBlank()) {
            return overrideName.trim();
        }
        String baseName = outputDir != null && outputDir.getFileName() != null
                ? outputDir.getFileName().toString()
                : (markdownPath.getParent() != null
                        ? markdownPath.getParent().getFileName().toString()
                        : markdownPath.getFileName().toString());
        return sanitizeSkillName(baseName);
    }

    public static String sanitizeSkillName(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9._-]+", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^[.-]+|[.-]+$", "");
        return normalized.isBlank() ? "generated-skill" : normalized;
    }

    public static void generateSkillSkeleton(Path markdownPath, Path outputDir, String overrideName) throws IOException {
        String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        String name = inferSkillName(markdownPath, outputDir, overrideName);
        String title = extractTitle(markdown, name);
        String description = extractSummary(markdown, "Generated skeleton from markdown skill description.");

        Files.createDirectories(outputDir);
        Path targetMarkdown = outputDir.resolve("SKILL.md");
        if (!Files.exists(targetMarkdown)) {
            Files.copy(markdownPath, targetMarkdown);
        }
        Path skillYaml = outputDir.resolve("skill.yaml");
        if (Files.exists(skillYaml)) {
            throw new IllegalStateException("Target skill.yaml already exists: " + skillYaml);
        }

        String yaml = """
                name: %s
                description: %s
                autoExecuteAllowed: false
                arguments: []
                steps:
                  - id: TODO_step
                    type: tool
                    toolName: llm
                    outputKey: result
                    arguments:
                      prompt: |
                        TODO: convert the human-oriented skill description in SKILL.md into an executable workflow.
                        Skill title: %s

                        Keep SKILL.md as the human description.
                        Replace this single llm step with concrete tool and llm steps when the workflow becomes clear.
                """.formatted(
                escapeYamlScalar(name),
                escapeYamlScalar(description),
                title.replace("\r", "").replace("\n", " ").trim());
        Files.writeString(skillYaml, yaml, StandardCharsets.UTF_8);
    }

    public static ConversionResult convertWithAiAssistance(
            Path markdownPath,
            Path outputDir,
            String overrideName,
            ToolExecutor toolExecutor,
            String provider,
            String model,
            boolean useAi) throws IOException {
        if (useAi) {
            try {
                ConversionResult aiResult =
                        tryGenerateSkillWithAi(markdownPath, outputDir, overrideName, toolExecutor, provider, model);
                if (aiResult != null) {
                    return aiResult;
                }
            } catch (Exception ignored) {
                // fallback to skeleton generation below
            }
        }

        generateSkillSkeleton(markdownPath, outputDir, overrideName);
        String skillName = inferSkillName(markdownPath, outputDir, overrideName);
        return new ConversionResult(
                false,
                true,
                skillName,
                outputDir.resolve("SKILL.md"),
                outputDir.resolve("skill.yaml"),
                useAi
                        ? "AI conversion was unavailable or invalid. Generated a local skeleton instead."
                        : "Generated a local skeleton without AI assistance.");
    }

    public static SkillDefinition buildRuntimeSkillDefinition(
            Path markdownPath,
            String overrideName,
            ToolExecutor toolExecutor,
            String provider,
            String model,
            boolean useAi) throws IOException {
        String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        String skillName = inferSkillName(markdownPath, markdownPath.getParent(), overrideName);
        String fallbackDescription = extractSummary(markdown, "Generated from markdown skill description.");

        Map<String, Object> structured = null;
        if (useAi) {
            try {
                structured = tryGenerateStructuredSkillMap(markdownPath, skillName, fallbackDescription, toolExecutor, provider, model);
            } catch (Exception ignored) {
                structured = null;
            }
        }
        if (structured == null) {
            return buildSkeletonSkillDefinition(markdownPath, skillName, fallbackDescription, markdown);
        }
        return toSkillDefinition(structured, markdownPath, markdown);
    }

    private static ConversionResult tryGenerateSkillWithAi(
            Path markdownPath,
            Path outputDir,
            String overrideName,
            ToolExecutor toolExecutor,
            String provider,
            String model) throws Exception {
        String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        String skillName = inferSkillName(markdownPath, outputDir, overrideName);
        String fallbackDescription = extractSummary(markdown, "Generated from markdown skill description.");
        Map<String, Object> normalized =
                tryGenerateStructuredSkillMap(markdownPath, skillName, fallbackDescription, toolExecutor, provider, model);
        if (!isValidSkillMap(normalized)) {
            return null;
        }

        Files.createDirectories(outputDir);
        Path markdownTarget = outputDir.resolve("SKILL.md");
        if (!Files.exists(markdownTarget)) {
            Files.copy(markdownPath, markdownTarget);
        }
        Path yamlTarget = outputDir.resolve("skill.yaml");
        if (Files.exists(yamlTarget)) {
            throw new IllegalStateException("Target skill.yaml already exists: " + yamlTarget);
        }
        Files.writeString(yamlTarget, YAML_MAPPER.writeValueAsString(normalized), StandardCharsets.UTF_8);
        return new ConversionResult(
                true,
                false,
                skillName,
                markdownTarget,
                yamlTarget,
                "Generated a YAML draft with AI assistance.");
    }

    private static Map<String, Object> tryGenerateStructuredSkillMap(
            Path markdownPath,
            String skillName,
            String fallbackDescription,
            ToolExecutor toolExecutor,
            String provider,
            String model) throws Exception {
        if (toolExecutor == null) {
            return null;
        }
        String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        Map<String, Object> llmArgs = new LinkedHashMap<>();
        llmArgs.put("provider", provider);
        llmArgs.put("model", model);
        llmArgs.put("format", "yaml");
        llmArgs.put("requiredKeys", "name,description,steps");
        llmArgs.put("systemPrompt", """
                You convert a human-oriented Markdown skill description into an executable YAML skill definition.
                Return YAML only.
                Use only supported step type values that fit this system.
                Prefer tool steps.
                If AI reasoning is required inside the workflow, use toolName: llm instead of any legacy ai step.
                Keep the workflow small and practical.
                """);
        llmArgs.put("prompt", buildAiConversionPrompt(skillName, fallbackDescription, markdown));

        ToolExecutionResult result = toolExecutor.execute("llm", JSON_MAPPER.writeValueAsString(llmArgs), true);
        if (!result.ok()) {
            return null;
        }
        Object structured = result.data().get("structured");
        if (!(structured instanceof Map<?, ?> rawMap)) {
            return null;
        }
        return normalizeGeneratedSkill(rawMap, skillName, fallbackDescription);
    }

    private static String buildAiConversionPrompt(String skillName, String description, String markdown) {
        return """
                Convert the following Markdown skill description into this project's executable skill YAML format.

                Required top-level keys:
                - name
                - description
                - autoExecuteAllowed
                - arguments
                - steps

                Constraints:
                - name must be '%s'
                - description should stay concise and practical
                - arguments must be a YAML list
                - steps must be a YAML list
                - prefer type: tool
                - if analysis or summarization is needed, use toolName: llm
                - never use legacy type: ai
                - if details are still ambiguous, keep one llm step placeholder rather than inventing fragile tool calls

                Fallback description:
                %s

                Markdown skill description:
                ---
                %s
                ---
                """.formatted(skillName, description, markdown.strip());
    }

    private static Map<String, Object> normalizeGeneratedSkill(
            Map<?, ?> rawMap, String skillName, String fallbackDescription) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", skillName);
        normalized.put("description", stringValue(rawMap.get("description"), fallbackDescription));
        normalized.put("autoExecuteAllowed", Boolean.FALSE.equals(rawMap.get("autoExecuteAllowed"))
                ? false
                : Boolean.TRUE.equals(rawMap.get("autoExecuteAllowed")));
        normalized.put("arguments", listValue(rawMap.get("arguments")));
        normalized.put("steps", listValue(rawMap.get("steps")));
        return normalized;
    }

    private static boolean isValidSkillMap(Map<String, Object> skillMap) {
        if (!(skillMap.get("name") instanceof String name) || name.isBlank()) {
            return false;
        }
        if (!(skillMap.get("description") instanceof String description) || description.isBlank()) {
            return false;
        }
        Object argumentsValue = skillMap.get("arguments");
        if (!(argumentsValue instanceof List<?> arguments) || !isValidArguments(arguments)) {
            return false;
        }
        Object stepsValue = skillMap.get("steps");
        if (!(stepsValue instanceof List<?> steps) || steps.isEmpty()) {
            return false;
        }
        for (Object step : steps) {
            if (!(step instanceof Map<?, ?> stepMap)) {
                return false;
            }
            if (!isValidStep(stepMap)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidArguments(List<?> arguments) {
        for (Object argument : arguments) {
            if (!(argument instanceof Map<?, ?> argumentMap)) {
                return false;
            }
            Object name = argumentMap.get("name");
            Object type = argumentMap.get("type");
            if (!(name instanceof String nameText) || nameText.isBlank()) {
                return false;
            }
            if (!(type instanceof String typeText) || typeText.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidStep(Map<?, ?> stepMap) {
        Object id = stepMap.get("id");
        Object type = stepMap.get("type");
        if (!(id instanceof String idText) || idText.isBlank()) {
            return false;
        }
        if (!(type instanceof String typeText) || typeText.isBlank()) {
            return false;
        }
        if (!"tool".equals(typeText)) {
            return false;
        }
        Object toolName = stepMap.get("toolName");
        Object outputKey = stepMap.get("outputKey");
        Object arguments = stepMap.get("arguments");
        if (!(toolName instanceof String toolNameText) || toolNameText.isBlank()) {
            return false;
        }
        if (!(outputKey instanceof String outputKeyText) || outputKeyText.isBlank()) {
            return false;
        }
        if (!(arguments instanceof Map<?, ?> argumentsMap)) {
            return false;
        }
        if ("llm".equals(toolNameText)) {
            Object prompt = argumentsMap.get("prompt");
            if (!(prompt instanceof String promptText) || promptText.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return fallback;
    }

    private static SkillDefinition buildSkeletonSkillDefinition(
            Path markdownPath,
            String skillName,
            String description,
            String markdown) {
        SkillDefinition definition = new SkillDefinition();
        definition.name = skillName;
        definition.description = description;
        definition.autoExecuteAllowed = false;
        definition.skillHome = markdownPath.toAbsolutePath().normalize().toString();
        definition.markdownHome = markdownPath.toAbsolutePath().normalize().toString();
        definition.markdownDescription = markdown;
        definition.generatedFromMarkdown = true;

        SkillStepDefinition step = new SkillStepDefinition();
        step.id = "TODO_step";
        step.type = "tool";
        step.toolName = "llm";
        step.outputKey = "result";
        step.arguments.put(
                "prompt",
                """
                        Convert the following markdown skill description into concrete execution steps and then execute them mentally as far as possible.

                        Keep this as a temporary runtime fallback.

                        %s
                        """.formatted(markdown.strip()));
        definition.steps.add(step);
        return definition;
    }

    private static SkillDefinition toSkillDefinition(Map<String, Object> structured, Path markdownPath, String markdown) {
        SkillDefinition definition = JSON_MAPPER.convertValue(structured, SkillDefinition.class);
        definition.skillHome = markdownPath.toAbsolutePath().normalize().toString();
        definition.markdownHome = markdownPath.toAbsolutePath().normalize().toString();
        definition.markdownDescription = markdown;
        definition.generatedFromMarkdown = true;
        return definition;
    }

    public static Map<String, Object> toPortableSkillMap(SkillDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", definition.name);
        map.put("description", definition.description);
        map.put("autoExecuteAllowed", definition.autoExecuteAllowed);
        if (!definition.parameterAliases.isEmpty()) {
            map.put("parameterAliases", definition.parameterAliases);
        }
        map.put("arguments", definition.arguments);
        map.put("steps", definition.steps);
        return map;
    }

    private static String escapeYamlScalar(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
