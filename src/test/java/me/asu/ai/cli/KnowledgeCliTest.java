package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.asu.ai.analyze.AnalyzeInputType;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeCliTest {

    @Test
    void shouldPrintUsage() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            KnowledgeCli.printUsage();
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("knowledge list"));
        assertTrue(text.contains("knowledge search"));
        assertTrue(text.contains("knowledge summarize"));
    }

    @Test
    void shouldListRecentKnowledge(@TempDir Path tempDir) throws Exception {
        Path configFile = prepareConfig(tempDir);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        store.recordAnalyze(
                "java.lang.NullPointerException",
                AnalyzeInputType.JAVA_EXCEPTION,
                "processed stack",
                """
                        [Problem Type]
                        NullPointerException
                        [Suggestions]
                        1. add null check
                        """);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            KnowledgeCli.main(new String[] {"list", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Knowledge dir:"));
        assertTrue(text.contains("NullPointerException"));
        assertTrue(text.contains("add null check"));
    }

    @Test
    void shouldSearchKnowledge(@TempDir Path tempDir) throws Exception {
        Path configFile = prepareConfig(tempDir);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        MethodInfo target = new MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.className = "OrderService";
        target.methodName = "createOrder";
        store.recordFixSuggestion(
                "add null checks for order creation",
                target,
                """
                        [Mode]
                        suggestion-only
                        [Understanding]
                        Null handling is missing before dereference.
                        [Suggestions]
                        1. Add null checks.
                        2. Keep existing return behavior.
                        """);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            KnowledgeCli.main(new String[] {"search", "--query", "createOrder null", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Mode: search: createOrder null"));
        assertTrue(text.contains("demo.OrderService#createOrder"));
        assertTrue(text.contains("Add null checks"));
    }

    @Test
    void shouldFilterKnowledgeSearchBySource(@TempDir Path tempDir) throws Exception {
        Path configFile = prepareConfig(tempDir);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        MethodInfo target = new MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.className = "OrderService";
        target.methodName = "createOrder";
        store.recordAnalyze(
                "java.lang.NullPointerException",
                AnalyzeInputType.JAVA_EXCEPTION,
                "processed stack",
                """
                        [Problem Type]
                        NullPointerException
                        [Suggestions]
                        1. add null check
                        """);
        store.recordFixSuggestion(
                "add null checks for order creation",
                target,
                """
                        [Mode]
                        suggestion-only
                        [Understanding]
                        Null handling is missing before dereference.
                        [Suggestions]
                        1. Add null checks.
                        """);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            KnowledgeCli.main(new String[] {
                    "search", "--query", "null", "--source", "fix", "--config", configFile.toString()
            });
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Filter source: fix"));
        assertTrue(text.contains("demo.OrderService#createOrder"));
        assertTrue(!text.contains("NullPointerException"));
    }

    @Test
    void shouldSummarizeKnowledge(@TempDir Path tempDir) throws Exception {
        Path configFile = prepareConfig(tempDir);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        MethodInfo target = new MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.className = "OrderService";
        target.methodName = "createOrder";

        store.recordAnalyze(
                "java.lang.NullPointerException",
                AnalyzeInputType.JAVA_EXCEPTION,
                "processed stack",
                """
                        [Problem Type]
                        NullPointerException
                        [Suggestions]
                        1. add null check
                        """);
        store.recordFixSuggestion(
                "add null checks for order creation",
                target,
                """
                        [Mode]
                        suggestion-only
                        [Understanding]
                        Null handling is missing before dereference.
                        [Suggestions]
                        1. Add null checks.
                        """);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            KnowledgeCli.main(new String[] {"summarize", "--top", "3", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Mode: summarize"));
        assertTrue(text.contains("Total records: 2"));
        assertTrue(text.contains("By source:"));
        assertTrue(text.contains("analyze: 1"));
        assertTrue(text.contains("fix: 1"));
    }

    @Test
    void shouldFilterKnowledgeSummaryByStatus(@TempDir Path tempDir) throws Exception {
        Path configFile = prepareConfig(tempDir);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        MethodInfo target = new MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.className = "OrderService";
        target.methodName = "createOrder";

        store.recordFixExecution("fix order null bug", target, true, "", "ai-fix/test");
        store.recordFixExecution("fix order null bug", target, false, "boom", "ai-fix/test");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            KnowledgeCli.main(new String[] {"summarize", "--status", "success", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Filter status: success"));
        assertTrue(text.contains("Total records: 1"));
        assertTrue(text.contains("success: 1"));
    }

    private Path prepareConfig(Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "app.home=" + tempDir.resolve("app-home").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        return configFile;
    }
}
