package me.asu.ai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.asu.ai.analyze.ProjectAnalyzer;
import me.asu.ai.analyze.ProjectAnalyzerFactory;
import me.asu.ai.analyze.ProjectLanguage;
import me.asu.ai.analyze.ProjectLanguageDetector;
import me.asu.ai.config.AppConfig;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.util.JacksonUtils;

public class AiUnderstandCLI {

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        String explicitConfigPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(explicitConfigPath);
        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }
        if (isConfigDebugEnabled(config, args)) {
            config.printDebugSummary();
        }

        String provider = config.get("provider", "openai");
        String model = config.get("model", "gpt-4.1");
        String sourceRoot = ".";
        String languageName = "auto";
        String outputJson = "project-summary.json";
        String outputMarkdown = "understand.md";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provider" -> provider = args[++i];
                case "--model" -> model = args[++i];
                case "--source-root" -> sourceRoot = args[++i];
                case "--language" -> languageName = args[++i];
                case "--output-json" -> outputJson = args[++i];
                case "--output-markdown" -> outputMarkdown = args[++i];
                case "--config" -> i++;
                default -> {
                    if (!args[i].startsWith("--")) {
                        sourceRoot = args[i];
                    }
                }
            }
        }

        Path root = Paths.get(sourceRoot).toAbsolutePath().normalize();
        System.out.println("Analyzing project: " + root);
        ProjectLanguage language = ProjectLanguageDetector.parseOrDetect(languageName, root);
        System.out.println("Detected language: " + language.name().toLowerCase());

        ProjectAnalyzer analyzer = ProjectAnalyzerFactory.create(language);
        ProjectSummary summary = analyzer.analyze(root);

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputJson), summary);
        System.out.println("Project summary written: " + outputJson);

        String markdown = buildMarkdown(summary, provider, model, config, mapper);
        Files.writeString(Paths.get(outputMarkdown), markdown, StandardCharsets.UTF_8);
        System.out.println("Project report written: " + outputMarkdown);
    }

    private static String buildMarkdown(
            ProjectSummary summary,
            String provider,
            String model,
            AppConfig config,
            ObjectMapper mapper) throws Exception {
        String fallback = buildFallbackMarkdown(summary);

        try {
            LLMClient llm = LLMFactory.create(provider, model, config);
            ProjectSummary slimmed = slimSummary(summary);
            String prompt = """
                    You are helping a developer quickly understand a project before making changes.
                    The project context includes statistics, class categories, and dependency links.

                    Write a concise Markdown report in Chinese with these sections:
                    1. 项目概览 (Include project name, primary language, and overall stats)
                    2. 技术架构与分层 (Based on architecturalLayers and identified categories)
                    3. 核心业务逻辑 (Highlight classes in coreLogic and logicFlows)
                    4. 外部集成与依赖 (Based on externalIntegrations and dependencies)
                    5. 入口点与运行方式 (Highlight cliClasses and entryPoints)
                    6. 建议阅读路径与后续 AI 修复任务建议

                    Rules:
                    - Be concrete and avoid fluff.
                    - Base conclusions on the provided facts only.
                    - Focus on the relationship between classes.
                    - Keep the report compact and useful for a senior developer.

                    Project facts JSON:
                    %s
                    """.formatted(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(slimmed));
            String result = llm.generate(prompt);
            if (result == null || result.isBlank()) {
                System.err.println("Warning: AI understand report is empty, fallback to local markdown.");
                return fallback;
            }
            return stripMarkdownFence(result);
        } catch (Exception e) {
            System.err.println("Warning: failed to generate AI understand report, fallback to local markdown. " + e.getMessage());
            return fallback;
        }
    }

    private static ProjectSummary slimSummary(ProjectSummary original) {
        ProjectSummary slim = new ProjectSummary();
        slim.projectName = original.projectName;
        slim.projectRoot = original.projectRoot;
        slim.primaryLanguage = original.primaryLanguage;
        slim.mavenProject = original.mavenProject;
        slim.gradleProject = original.gradleProject;
        slim.buildFiles = original.buildFiles;
        slim.sourceFileCount = original.sourceFileCount;
        slim.javaFileCount = original.javaFileCount;
        slim.testFileCount = original.testFileCount;
        slim.packageCount = original.packageCount;
        slim.classCount = original.classCount;
        slim.methodCount = original.methodCount;
        slim.controllerCount = original.controllerCount;
        slim.serviceCount = original.serviceCount;
        slim.repositoryCount = original.repositoryCount;
        slim.configCount = original.configCount;

        // Limit lists to essential items for the LLM
        slim.entryPoints = limit(original.entryPoints, 5);
        slim.packages = limit(original.packages, 10);
        slim.externalIntegrations = limit(original.externalIntegrations, 10);
        slim.topClasses = limit(original.topClasses, 5);
        slim.controllers = limit(original.controllers, 5);
        slim.services = limit(original.services, 5);
        slim.repositories = limit(original.repositories, 5);
        slim.configs = limit(original.configs, 5);
        slim.cliClasses = limit(original.cliClasses, 5);
        slim.coreLogic = limit(original.coreLogic, 10);
        
        slim.architecturalLayers = original.architecturalLayers;
        slim.dependencies = limit(original.dependencies, 20);
        slim.logicFlows = original.logicFlows;
        
        return slim;
    }

    private static <T> java.util.List<T> limit(java.util.List<T> list, int n) {
        if (list == null) return new java.util.ArrayList<>();
        if (list.size() <= n) return list;
        return list.subList(0, n);
    }

    private static String buildFallbackMarkdown(ProjectSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project Understanding Report\n\n");
        sb.append("## Project Overview\n\n");
        sb.append("- Project name: ").append(summary.projectName).append("\n");
        sb.append("- Build files: ")
                .append(summary.buildFiles.isEmpty() ? "Not identified" : String.join(", ", summary.buildFiles))
                .append("\n");
        sb.append("- Java files: ").append(summary.javaFileCount).append(" (Test files: ").append(summary.testFileCount).append(")\n");
        sb.append("- Methods: ").append(summary.methodCount).append("\n\n");

        sb.append("## Architectural Layers\n\n");
        if (summary.architecturalLayers.isEmpty()) {
            sb.append("- Not explicitly identified\n");
        } else {
            summary.architecturalLayers.forEach(layer -> sb.append("- ").append(layer).append("\n"));
        }
        sb.append("\n");

        sb.append("## Core Components\n\n");
        appendComponents(sb, "CLI/Entry Points", summary.cliClasses);
        appendComponents(sb, "Core Logic", summary.coreLogic);
        appendComponents(sb, "Top Classes (Complexity)", summary.topClasses);
        appendComponents(sb, "Controllers", summary.controllers);
        appendComponents(sb, "Services", summary.services);

        sb.append("\n## External Integrations\n\n");
        sb.append("- ").append(summary.externalIntegrations.isEmpty()
                ? "No obvious integration was identified"
                : String.join(", ", summary.externalIntegrations)).append("\n");

        sb.append("\n## Dependencies (Sample)\n\n");
        if (summary.dependencies.isEmpty()) {
            sb.append("- No internal dependencies identified\n");
        } else {
            summary.dependencies.stream().limit(10).forEach(d -> 
                sb.append("- ").append(d.source).append(" -> ").append(d.target).append(" (").append(d.type).append(")\n"));
        }

        sb.append("\n## Suggested Reading Path\n\n");
        if (!summary.cliClasses.isEmpty()) {
            sb.append("- Start from the CLI layer (").append(summary.cliClasses.get(0).className).append(") to understand the user interface and orchestration.\n");
        }
        if (!summary.coreLogic.isEmpty()) {
            sb.append("- Dive into core logic (").append(summary.coreLogic.get(0).className).append(") to understand the main business processing.\n");
        }

        return sb.toString();
    }

    private static void appendComponents(
            StringBuilder sb,
            String title,
            java.util.List<ProjectSummary.ComponentSummary> components) {
        if (components.isEmpty()) return;
        sb.append("### ").append(title).append("\n\n");
        for (ProjectSummary.ComponentSummary component : components) {
            sb.append("  - ")
                    .append(component.className)
                    .append(" [").append(component.packageName).append("], methods: ")
                    .append(component.methodCount);
            if (!component.annotations.isEmpty()) {
                sb.append(", annotations: ").append(String.join(", ", component.annotations));
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar understand [source-root] [options]

                Options:
                  --source-root <path>       Project root to analyze
                  --language <name>          auto | java | go | python
                  --output-json <path>       Output JSON summary path
                  --output-markdown <path>   Output Markdown report path
                  --provider <provider>      openai | groq | ollama
                  --model <modelName>        Override configured model
                  --config <path>            Load an explicit config file
                  --help                     Show this help
                  --config-debug             Print config loading summary
                """);
    }

    private static boolean containsHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigDebugEnabled(AppConfig config, String[] args) {
        for (String arg : args) {
            if ("--config-debug".equals(arg)) {
                return true;
            }
        }
        return "true".equalsIgnoreCase(config.get("config.debug"))
                || "1".equals(config.get("config.debug"));
    }

    private static String findOptionValue(String[] args, String optionName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:markdown|md)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim() + "\n";
    }
}
