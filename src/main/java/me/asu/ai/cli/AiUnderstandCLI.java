package me.asu.ai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.asu.ai.analyze.JavaProjectAnalyzer;
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
        String outputJson = "project-summary.json";
        String outputMarkdown = "understand.md";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provider" -> provider = args[++i];
                case "--model" -> model = args[++i];
                case "--source-root" -> sourceRoot = args[++i];
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

        JavaProjectAnalyzer analyzer = new JavaProjectAnalyzer();
        ProjectSummary summary = analyzer.analyze(root);

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(outputJson), summary);
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
                    1. \u9879\u76ee\u6982\u89c8
                    2. \u6280\u672f\u4e0e\u7ed3\u6784
                    3. \u4e3b\u8981\u5165\u53e3\u4e0e\u5173\u952e\u7ec4\u4ef6
                    4. \u5916\u90e8\u96c6\u6210\u4e0e\u4f9d\u8d56\u9762
                    5. \u5efa\u8bae\u9605\u8bfb\u8def\u5f84
                    6. \u540e\u7eed\u7ed9 AI \u4fee\u590d\u4efb\u52a1\u65f6\u6700\u91cd\u8981\u7684\u4e0a\u4e0b\u6587

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
        sb.append("# \u9879\u76ee\u7406\u89e3\u62a5\u544a\n\n");
        sb.append("## \u9879\u76ee\u6982\u89c8\n\n");
        sb.append("- \u9879\u76ee\u6839\u76ee\u5f55\uff1a").append(summary.projectRoot).append("\n");
        sb.append("- \u9879\u76ee\u540d\u79f0\uff1a").append(summary.projectName).append("\n");
        sb.append("- \u6784\u5efa\u6587\u4ef6\uff1a")
                .append(summary.buildFiles.isEmpty() ? "\u672a\u8bc6\u522b" : String.join(", ", summary.buildFiles))
                .append("\n");
        sb.append("- Java \u6587\u4ef6\u6570\uff1a").append(summary.javaFileCount).append("\n");
        sb.append("- \u5305\u6570\u91cf\uff1a").append(summary.packageCount).append("\n");
        sb.append("- \u7c7b\u6570\u91cf\uff1a").append(summary.classCount).append("\n");
        sb.append("- \u63a5\u53e3\u6570\u91cf\uff1a").append(summary.interfaceCount).append("\n");
        sb.append("- \u679a\u4e3e\u6570\u91cf\uff1a").append(summary.enumCount).append("\n");
        sb.append("- \u65b9\u6cd5\u6570\u91cf\uff1a").append(summary.methodCount).append("\n\n");

        sb.append("## \u6280\u672f\u4e0e\u7ed3\u6784\n\n");
        sb.append("- Controller\uff1a").append(summary.controllerCount).append("\n");
        sb.append("- Service\uff1a").append(summary.serviceCount).append("\n");
        sb.append("- Repository\uff1a").append(summary.repositoryCount).append("\n");
        sb.append("- Configuration\uff1a").append(summary.configCount).append("\n");
        sb.append("- Component\uff1a").append(summary.componentCount).append("\n");
        sb.append("- \u4e3b\u8981\u5305\uff1a")
                .append(summary.packages.isEmpty() ? "\u672a\u8bc6\u522b" : String.join(", ", summary.packages))
                .append("\n\n");

        sb.append("## \u4e3b\u8981\u5165\u53e3\u4e0e\u5173\u952e\u7ec4\u4ef6\n\n");
        sb.append("- \u5165\u53e3\u70b9\uff1a")
                .append(summary.entryPoints.isEmpty() ? "\u672a\u8bc6\u522b" : String.join(", ", summary.entryPoints))
                .append("\n");
        appendComponents(sb, "Top \u7c7b", summary.topClasses);
        appendComponents(sb, "Controller", summary.controllers);
        appendComponents(sb, "Service", summary.services);

        sb.append("\n## \u5916\u90e8\u96c6\u6210\u4e0e\u4f9d\u8d56\u9762\n\n");
        sb.append("- ").append(summary.externalIntegrations.isEmpty()
                ? "\u672a\u8bc6\u522b\u660e\u663e\u96c6\u6210"
                : String.join(", ", summary.externalIntegrations)).append("\n");

        sb.append("\n## \u5efa\u8bae\u9605\u8bfb\u8def\u5f84\n\n");
        if (!summary.controllers.isEmpty()) {
            sb.append("- \u5148\u4ece Controller \u5c42\u5f00\u59cb\uff0c\u7406\u89e3\u5bf9\u5916\u5165\u53e3\u548c\u8bf7\u6c42\u6d41\u5411\u3002\n");
        } else if (!summary.entryPoints.isEmpty()) {
            sb.append("- \u5148\u4ece\u542f\u52a8\u5165\u53e3\u548c\u4e3b\u6d41\u7a0b\u7c7b\u5f00\u59cb\uff0c\u7406\u89e3\u7cfb\u7edf\u88c5\u914d\u65b9\u5f0f\u3002\n");
        } else {
            sb.append("- \u5148\u4ece\u65b9\u6cd5\u6570\u6700\u591a\u7684\u6838\u5fc3\u7c7b\u5f00\u59cb\uff0c\u518d\u56de\u770b\u914d\u7f6e\u4e0e\u96c6\u6210\u70b9\u3002\n");
        }
        if (!summary.services.isEmpty()) {
            sb.append("- \u518d\u9605\u8bfb\u4e3b\u8981 Service\uff0c\u786e\u8ba4\u4e1a\u52a1\u7f16\u6392\u548c\u4f9d\u8d56\u5173\u7cfb\u3002\n");
        }
        if (!summary.repositories.isEmpty()) {
            sb.append("- \u6700\u540e\u67e5\u770b Repository \u4e0e\u6301\u4e45\u5316\u5c42\uff0c\u786e\u8ba4\u6570\u636e\u8bbf\u95ee\u6a21\u5f0f\u3002\n");
        }

        sb.append("\n## \u540e\u7eed\u7ed9 AI \u4fee\u590d\u4efb\u52a1\u65f6\u6700\u91cd\u8981\u7684\u4e0a\u4e0b\u6587\n\n");
        sb.append("- \u4f18\u5148\u544a\u77e5\u9879\u76ee\u5206\u5c42\u3001\u4e3b\u8981\u5165\u53e3\u3001\u5173\u952e Service \u548c\u5916\u90e8\u96c6\u6210\u70b9\u3002\n");
        sb.append("- \u5982\u679c\u8981\u4fee\u6539\u4e1a\u52a1\u903b\u8f91\uff0c\u5148\u7ed3\u5408\u5bf9\u5e94 Controller/Service/Repository \u94fe\u8def\u63d0\u4f9b\u4e0a\u4e0b\u6587\u3002\n");
        sb.append("- \u5982\u679c\u8981\u4fee\u6539\u914d\u7f6e\u6216\u96c6\u6210\u4ee3\u7801\uff0c\u5148\u786e\u8ba4\u76f8\u5173 Configuration \u548c\u7b2c\u4e09\u65b9\u4f9d\u8d56\u4f7f\u7528\u65b9\u5f0f\u3002\n");
        return sb.toString();
    }

    private static void appendComponents(StringBuilder sb, String title, java.util.List<ProjectSummary.ComponentSummary> components) {
        sb.append("- ").append(title).append("\uff1a");
        if (components.isEmpty()) {
            sb.append("\u672a\u8bc6\u522b\n");
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
                    .append("\uff0c\u65b9\u6cd5\u6570 ")
                    .append(component.methodCount);
            if (!component.annotations.isEmpty()) {
                sb.append("\uff0c\u6ce8\u89e3 ").append(String.join(", ", component.annotations));
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
