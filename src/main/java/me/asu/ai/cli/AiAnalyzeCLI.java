package me.asu.ai.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import me.asu.ai.analyze.AnalyzeInputClassifier;
import me.asu.ai.analyze.AnalyzeInputType;
import me.asu.ai.analyze.AnalyzeModelRouter;
import me.asu.ai.analyze.AnalyzePreprocessor;
import me.asu.ai.analyze.AnalyzePromptBuilder;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;

public class AiAnalyzeCLI {

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }

        String explicitConfigPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(explicitConfigPath);
        if (isConfigDebugEnabled(config, args)) {
            config.printDebugSummary();
        }

        String requestedProvider = findOptionValue(args, "--provider");
        String requestedModel = findOptionValue(args, "--model");
        String input = readInput(args);
        if (input == null || input.isBlank()) {
            System.out.println(AnalyzePromptBuilder.buildUnknownFallback());
            return;
        }

        AnalyzeInputType type = AnalyzeInputClassifier.classify(input);
        String processed = AnalyzePreprocessor.process(input, type);
        if (processed.isBlank()) {
            System.out.println(AnalyzePromptBuilder.buildUnknownFallback());
            return;
        }

        String result;
        try {
            AnalyzeModelRouter.Selection selection =
                    AnalyzeModelRouter.select(requestedProvider, requestedModel, type, processed, config);
            LLMClient client = LLMFactory.create(selection.provider(), selection.model(), config);
            result = client.generate(AnalyzePromptBuilder.build(type, processed));
            if (result == null || result.isBlank()) {
                result = AnalyzePromptBuilder.buildUnknownFallback();
            }
        } catch (Exception primaryError) {
            result = fallbackToOpenAI(requestedModel, type, processed, config, primaryError);
        }

        System.out.println(stripMarkdownFence(result));
        new ProblemKnowledgeStore(config).recordAnalyze(input, type, processed, stripMarkdownFence(result));
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar analyze [text] [options]
                  cat error.log | java -jar ai-fix-<version>.jar analyze [options]

                Options:
                  --provider <provider>       openai | groq | ollama
                  --model <modelName>         Override configured model
                  --config <path>             Load an explicit config file
                  --help                      Show this help
                  --config-debug              Print config loading summary

                Notes:
                  - stdin input has higher priority than command text
                  - this command only analyzes and suggests next steps
                  - it never edits code, runs deploy, or invokes tools
                """);
    }

    static String readInput(String[] args) throws IOException {
        if (System.console() == null) {
            byte[] bytes = System.in.readAllBytes();
            String piped = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!piped.isBlank()) {
                return piped;
            }
        }
        return extractTextArgument(args);
    }

    private static String extractTextArgument(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (expectsValue(arg)) {
                i++;
                continue;
            }
            if (arg.startsWith("--")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(arg);
        }
        return builder.toString().trim();
    }

    private static boolean expectsValue(String arg) {
        return "--provider".equals(arg)
                || "--model".equals(arg)
                || "--config".equals(arg);
    }

    private static String fallbackToOpenAI(
            String requestedModel,
            AnalyzeInputType type,
            String processed,
            AppConfig config,
            Exception primaryError) {
        try {
            LLMClient fallbackClient = LLMFactory.create(
                    "openai",
                    requestedModel != null && !requestedModel.isBlank() ? requestedModel : config.get("model", "gpt-4.1"),
                    config);
            return fallbackClient.generate(AnalyzePromptBuilder.build(type, processed));
        } catch (Exception fallbackError) {
            System.err.println("Analyze fallback failed after primary error: " + primaryError.getMessage());
            System.err.println("OpenAI fallback error: " + fallbackError.getMessage());
            return AnalyzePromptBuilder.buildUnknownFallback();
        }
    }

    private static boolean containsHelpFlag(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> "--help".equals(arg) || "-h".equals(arg));
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
            trimmed = trimmed.replaceFirst("^```(?:markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}
