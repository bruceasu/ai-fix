package me.asu.ai.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import me.asu.ai.analyze.AnalyzeInputClassifier;
import me.asu.ai.analyze.AnalyzeInputType;
import me.asu.ai.analyze.AnalyzeModelRouter;
import me.asu.ai.analyze.AnalyzePreprocessor;
import me.asu.ai.analyze.AnalyzePromptBuilder;
import me.asu.ai.analyze.ProjectAnalyzer;
import me.asu.ai.analyze.ProjectAnalyzerFactory;
import me.asu.ai.analyze.ProjectLanguage;
import me.asu.ai.analyze.ProjectLanguageDetector;
import me.asu.ai.cli.IndexCli;
import me.asu.ai.config.AppConfig;
import me.asu.ai.fix.FixSuggestionGenerator;
import me.asu.ai.fix.FixTargetMatcher;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;

public class CommandBridgeSupport {

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public CommandBridgeSupport(AppConfig config) {
        this.config = config == null ? AppConfig.load() : config;
    }

    public ToolExecutionResult executeAnalyze(String toolName, JsonNode args) {
        String input = args.path("input").asText("");
        String provider = blankToNull(args.path("provider").asText(""));
        String model = blankToNull(args.path("model").asText(""));
        if (input.isBlank()) {
            String fallback = AnalyzePromptBuilder.buildUnknownFallback();
            return ToolExecutionResult.success(toolName, fallback, Map.of(
                    "command", "analyze",
                    "inputType", "unknown",
                    "fallback", true));
        }
        AnalyzeInputType type = AnalyzeInputClassifier.classify(input);
        String processed = AnalyzePreprocessor.process(input, type);
        if (processed.isBlank()) {
            String fallback = AnalyzePromptBuilder.buildUnknownFallback();
            return ToolExecutionResult.success(toolName, fallback, Map.of(
                    "command", "analyze",
                    "inputType", "unknown",
                    "fallback", true));
        }
        try {
            AnalyzeModelRouter.Selection selection =
                    AnalyzeModelRouter.select(provider, model, type, processed, config);
            LLMClient client = LLMFactory.create(selection.provider(), selection.model(), config);
            String output = safeText(client.generate(AnalyzePromptBuilder.build(type, processed)));
            if (output.isBlank()) {
                output = AnalyzePromptBuilder.buildUnknownFallback();
            }
            return ToolExecutionResult.success(toolName, output, Map.of(
                    "command", "analyze",
                    "inputType", type.name().toLowerCase(),
                    "provider", selection.provider(),
                    "model", selection.model(),
                    "processedLength", processed.length()));
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, e.getMessage());
        }
    }

    public ToolExecutionResult executeIndex(String toolName, JsonNode args) {
        try {
            Path sourceRoot = Path.of(args.path("sourceRoot").asText(".")).toAbsolutePath().normalize();
            Path outputPath = Path.of(args.path("outputPath").asText("index.json")).toAbsolutePath().normalize();
            String languageOption = args.path("language").asText("auto");
            ProjectLanguage language = ProjectLanguageDetector.parseOrDetect(languageOption, sourceRoot);
            List<MethodInfo> index = IndexCli.build(sourceRoot, language);
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), index);
            String output = "index.json generated: " + outputPath;
            return ToolExecutionResult.success(toolName, output, Map.of(
                    "command", "index",
                    "language", language.name().toLowerCase(),
                    "sourceRoot", sourceRoot.toString().replace('\\', '/'),
                    "outputPath", outputPath.toString().replace('\\', '/'),
                    "symbolCount", index.size()));
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, e.getMessage());
        }
    }

    public ToolExecutionResult executeUnderstand(String toolName, JsonNode args) {
        try {
            Path sourceRoot = Path.of(args.path("sourceRoot").asText(".")).toAbsolutePath().normalize();
            String languageOption = args.path("language").asText("auto");
            String provider = valueOrDefault(blankToNull(args.path("provider").asText("")), config.get("provider", AppConfig.DEFAULT_PROVIDER));
            String model = valueOrDefault(blankToNull(args.path("model").asText("")), config.get("model", AppConfig.DEFAULT_MODEL));
            Path outputJson = Path.of(args.path("outputJson").asText("project-summary.json")).toAbsolutePath().normalize();
            Path outputMarkdown = Path.of(args.path("outputMarkdown").asText("understand.md")).toAbsolutePath().normalize();

            ProjectLanguage language = ProjectLanguageDetector.parseOrDetect(languageOption, sourceRoot);
            ProjectAnalyzer analyzer = ProjectAnalyzerFactory.create(language);
            ProjectSummary summary = analyzer.analyze(sourceRoot);
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputJson.toFile(), summary);

            String markdown = buildUnderstandMarkdown(summary, provider, model);
            Files.writeString(outputMarkdown, markdown, StandardCharsets.UTF_8);

            return ToolExecutionResult.success(toolName, markdown, Map.of(
                    "command", "understand",
                    "language", language.name().toLowerCase(),
                    "sourceRoot", sourceRoot.toString().replace('\\', '/'),
                    "outputJson", outputJson.toString().replace('\\', '/'),
                    "outputMarkdown", outputMarkdown.toString().replace('\\', '/'),
                    "projectName", safeText(summary.projectName)));
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, e.getMessage());
        }
    }

    public ToolExecutionResult executeFixSuggest(String toolName, JsonNode args) {
        try {
            String task = args.path("task").asText("");
            if (task.isBlank()) {
                return ToolExecutionResult.failure(toolName, "Missing required argument: task");
            }
            Path indexPath = Path.of(args.path("indexPath").asText("index.json")).toAbsolutePath().normalize();
            if (!Files.isRegularFile(indexPath)) {
                return ToolExecutionResult.failure(toolName, "index file not found: " + indexPath);
            }
            List<MethodInfo> index = mapper.readValue(indexPath.toFile(), new TypeReference<List<MethodInfo>>() {
            });
            String match = blankToNull(args.path("match").asText(""));
            String symbol = blankToNull(args.path("symbol").asText(""));
            String container = blankToNull(args.path("container").asText(""));
            String packageName = blankToNull(args.path("package").asText(""));
            String fileFilter = blankToNull(args.path("file").asText(""));
            int limit = Math.max(1, args.path("limit").asInt(5));

            FixTargetMatcher matcher = new FixTargetMatcher();
            List<MethodInfo> targets = matcher.filterTargets(
                    index,
                    match,
                    symbol,
                    packageName,
                    container,
                    fileFilter,
                    null,
                    null);
            if (targets.isEmpty()) {
                return ToolExecutionResult.failure(toolName, "No matching symbols found");
            }
            if (targets.size() > 1 && (container == null || container.isBlank())) {
                String candidates = formatFixCandidates(targets, limit);
                return ToolExecutionResult.success(toolName, candidates, Map.of(
                        "command", "fix-suggest",
                        "matchType", "candidates",
                        "matchCount", targets.size(),
                        "limit", limit,
                        "indexPath", indexPath.toString().replace('\\', '/')));
            }

            MethodInfo target = targets.getFirst();
            String provider = valueOrDefault(blankToNull(args.path("provider").asText("")), config.get("provider", AppConfig.DEFAULT_PROVIDER));
            String model = valueOrDefault(blankToNull(args.path("model").asText("")), config.get("model", AppConfig.DEFAULT_MODEL));
            ProjectSummary projectSummary = loadProjectSummary(blankToNull(args.path("projectSummaryPath").asText("")));
            LLMClient llm = LLMFactory.create(provider, model, config);
            FixSuggestionGenerator generator = new FixSuggestionGenerator(llm, config, projectSummary);
            String suggestion = generator.generate(task, target);
            return ToolExecutionResult.success(toolName, suggestion, Map.of(
                    "command", "fix-suggest",
                    "matchType", "target",
                    "provider", provider,
                    "model", model,
                    "matchCount", targets.size(),
                    "target", safeText(target.className) + "#" + safeText(target.methodName),
                    "indexPath", indexPath.toString().replace('\\', '/')));
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, e.getMessage());
        }
    }

    private ProjectSummary loadProjectSummary(String projectSummaryPath) throws Exception {
        if (projectSummaryPath == null || projectSummaryPath.isBlank()) {
            Path defaultPath = Path.of("project-summary.json").toAbsolutePath().normalize();
            if (!Files.isRegularFile(defaultPath)) {
                return null;
            }
            return mapper.readValue(defaultPath.toFile(), ProjectSummary.class);
        }
        Path path = Path.of(projectSummaryPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return mapper.readValue(path.toFile(), ProjectSummary.class);
    }

    private String buildUnderstandMarkdown(ProjectSummary summary, String provider, String model) throws Exception {
        try {
            LLMClient llm = LLMFactory.create(provider, model, config);
            String prompt = """
                    You are helping a developer quickly understand a software project before making changes.

                    Write a concise Markdown report in Chinese with these sections:
                    1. 项目概览
                    2. 技术与结构
                    3. 主要入口与关键组件
                    4. 外部集成与依赖面
                    5. 建议阅读路径
                    6. 后续给 AI 修复任务时最重要的上下文

                    Rules:
                    - Be concrete and avoid fluff
                    - Base conclusions on the provided facts only
                    - If something is uncertain, say it is a guess
                    - Keep the report compact and useful

                    Project facts JSON:
                    %s
                    """.formatted(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
            String result = safeText(llm.generate(prompt));
            if (!result.isBlank()) {
                return stripFence(result);
            }
        } catch (Exception ignored) {
        }
        return """
                # Project Understanding Report

                - Project: %s
                - Root: %s
                - Primary language: %s
                - Source files: %d
                - Entry points: %s
                """.formatted(
                safeText(summary.projectName),
                safeText(summary.projectRoot),
                safeText(summary.primaryLanguage),
                summary.sourceFileCount,
                summary.entryPoints.isEmpty() ? "none" : String.join(", ", summary.entryPoints));
    }

    private String formatFixCandidates(List<MethodInfo> targets, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("[candidates]\n");
        targets.stream()
                .sorted(Comparator.comparing((MethodInfo m) -> safeText(m.className), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(m -> safeText(m.methodName), String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .forEachOrdered(method -> sb.append("- ")
                        .append(safeText(method.className))
                        .append("#")
                        .append(safeText(method.methodName))
                        .append(" | ")
                        .append(safeText(method.file))
                        .append(":")
                        .append(method.beginLine)
                        .append("-")
                        .append(method.endLine)
                        .append("\n"));
        if (targets.size() > limit) {
            sb.append("... and ").append(targets.size() - limit).append(" more candidates\n");
        }
        sb.append("\nProvide container or file to narrow the target before requesting a fix suggestion.");
        return sb.toString();
    }

    private String stripFence(String text) {
        String trimmed = safeText(text).trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim() + "\n";
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private String valueOrDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}
