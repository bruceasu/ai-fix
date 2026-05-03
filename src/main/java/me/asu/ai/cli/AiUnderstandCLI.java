package me.asu.ai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
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

public class AiUnderstandCLI {

    public static void main(String[] args) throws Exception {
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
            String prompt = """
                    You are helping a developer quickly understand a Java project before making changes.

                    Write a concise Markdown report in Chinese with these sections:
                    1. 项目概览
                    2. 技术与结构
                    3. 主要入口与关键组件
                    4. 外部集成与依赖面
                    5. 建议阅读路径
                    6. 后续给 AI 修复任务时最重要的上下文

                    Rules:
                    - Be concrete and avoid fluff.
                    - Base conclusions on the provided facts only.
                    - If something is uncertain, say it is a guess.
                    - Keep the report compact and useful.

                    Project facts JSON:
                    %s
                    """.formatted(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
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

    private static String buildFallbackMarkdown(ProjectSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project Understanding Report\n\n");
        sb.append("## Project Overview\n\n");
        sb.append("- Project root: ").append(summary.projectRoot).append("\n");
        sb.append("- Project name: ").append(summary.projectName).append("\n");
        sb.append("- Build files: ")
                .append(summary.buildFiles.isEmpty() ? "Not identified" : String.join(", ", summary.buildFiles))
                .append("\n");
        sb.append("- Java files: ").append(summary.javaFileCount).append("\n");
        sb.append("- Packages: ").append(summary.packageCount).append("\n");
        sb.append("- Classes: ").append(summary.classCount).append("\n");
        sb.append("- Interfaces: ").append(summary.interfaceCount).append("\n");
        sb.append("- Enums: ").append(summary.enumCount).append("\n");
        sb.append("- Methods: ").append(summary.methodCount).append("\n\n");

        sb.append("## Technology And Structure\n\n");
        sb.append("- Controllers: ").append(summary.controllerCount).append("\n");
        sb.append("- Services: ").append(summary.serviceCount).append("\n");
        sb.append("- Repositories: ").append(summary.repositoryCount).append("\n");
        sb.append("- Configurations: ").append(summary.configCount).append("\n");
        sb.append("- Components: ").append(summary.componentCount).append("\n");
        sb.append("- Main packages: ")
                .append(summary.packages.isEmpty() ? "Not identified" : String.join(", ", summary.packages))
                .append("\n\n");

        sb.append("## Main Entry Points And Key Components\n\n");
        sb.append("- Entry points: ")
                .append(summary.entryPoints.isEmpty() ? "Not identified" : String.join(", ", summary.entryPoints))
                .append("\n");
        appendComponents(sb, "Top Classes", summary.topClasses);
        appendComponents(sb, "Controllers", summary.controllers);
        appendComponents(sb, "Services", summary.services);

        sb.append("\n## External Integrations And Dependency Surface\n\n");
        sb.append("- ").append(summary.externalIntegrations.isEmpty()
                ? "No obvious integration was identified"
                : String.join(", ", summary.externalIntegrations)).append("\n");

        sb.append("\n## Suggested Reading Path\n\n");
        if (!summary.controllers.isEmpty()) {
            sb.append("- Start from the controller layer to understand external entry points and request flow.\n");
        } else if (!summary.entryPoints.isEmpty()) {
            sb.append("- Start from the application entry point and main flow classes to understand system wiring.\n");
        } else {
            sb.append("- Start from the core classes with the highest method count, then trace configuration and integration points.\n");
        }
        if (!summary.services.isEmpty()) {
            sb.append("- Continue with the main service classes to understand orchestration and dependencies.\n");
        }
        if (!summary.repositories.isEmpty()) {
            sb.append("- Finish with repositories and persistence-related classes to understand data access patterns.\n");
        }

        sb.append("\n## Most Important Context For Future AI Repair Tasks\n\n");
        sb.append("- Share the project layering, main entry points, key services, and external integrations first.\n");
        sb.append("- For business logic changes, include the related controller, service, and repository path.\n");
        sb.append("- For configuration or integration changes, include the relevant configuration classes and third-party usage patterns.\n");
        return sb.toString();
    }

    private static void appendComponents(
            StringBuilder sb,
            String title,
            java.util.List<ProjectSummary.ComponentSummary> components) {
        sb.append("- ").append(title).append(": ");
        if (components.isEmpty()) {
            sb.append("Not identified\n");
            return;
        }
        sb.append("\n");
        for (ProjectSummary.ComponentSummary component : components) {
            sb.append("  - ")
                    .append(component.className)
                    .append(" [")
                    .append(component.kind)
                    .append("] ")
                    .append(component.packageName)
                    .append(", method count ")
                    .append(component.methodCount);
            if (!component.annotations.isEmpty()) {
                sb.append(", annotations ").append(String.join(", ", component.annotations));
            }
            sb.append("\n");
        }
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
