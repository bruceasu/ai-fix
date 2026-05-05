package me.asu.ai.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import me.asu.ai.analyze.AnalyzeInputType;
import me.asu.ai.config.AppConfig;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemKnowledgeStoreTest {

    @Test
    void shouldExtractStructuredSections() {
        String text = """
                [Problem Type]
                NullPointerException
                [Possible Causes]
                1. a
                2. b
                [Suggestions]
                1. add null check
                2. keep behavior
                """;

        assertEquals("NullPointerException", ProblemKnowledgeStore.extractSection(text, "[Problem Type]"));
        assertTrue(ProblemKnowledgeStore.extractSection(text, "[Suggestions]").contains("add null check"));
    }

    @Test
    void shouldPersistAnalyzeRecord(@TempDir Path tempDir) throws Exception {
        AppConfig config = loadConfig(tempDir);
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);

        store.recordAnalyze(
                "java.lang.NullPointerException",
                AnalyzeInputType.JAVA_EXCEPTION,
                "processed stack trace",
                """
                        [Problem Type]
                        NullPointerException
                        [Suggestions]
                        1. add null check
                        2. preserve behavior
                        """);

        Path jsonl = config.getKnowledgeDirectory().resolve("records.jsonl");
        assertTrue(Files.isRegularFile(jsonl));
        String line = Files.readString(jsonl, StandardCharsets.UTF_8).trim();
        Map<?, ?> record = new ObjectMapper().readValue(line, Map.class);
        assertEquals("analyze", record.get("source"));
        assertEquals("NullPointerException", record.get("classification"));
        assertTrue(String.valueOf(record.get("repairScheme")).contains("add null check"));
        assertTrue(Files.list(config.getKnowledgeDirectory().resolve("records")).findAny().isPresent());
    }

    @Test
    void shouldPersistFixSuggestionRecord(@TempDir Path tempDir) throws Exception {
        AppConfig config = loadConfig(tempDir);
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        MethodInfo target = new MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.beginLine = 10;
        target.endLine = 20;
        target.symbolType = "method";
        target.signature = "createOrder(String id)";

        store.recordFixSuggestion(
                "add null checks",
                target,
                """
                        [Mode]
                        suggestion-only
                        [Target]
                        demo.OrderService#createOrder
                        [Understanding]
                        Null handling is missing before dereference.
                        [Suggestions]
                        1. Add null checks.
                        2. Keep existing return behavior.
                        """);

        Path jsonl = config.getKnowledgeDirectory().resolve("records.jsonl");
        String line = Files.readString(jsonl, StandardCharsets.UTF_8).trim();
        Map<?, ?> record = new ObjectMapper().readValue(line, Map.class);
        assertEquals("fix", record.get("source"));
        assertEquals("suggestion-only", record.get("mode"));
        assertEquals("demo.OrderService#createOrder", record.get("target"));
        assertTrue(String.valueOf(record.get("repairScheme")).contains("Add null checks"));
    }

    @Test
    void shouldFindRelevantRecords(@TempDir Path tempDir) throws Exception {
        AppConfig config = loadConfig(tempDir);
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
                        """);
        store.recordAnalyze(
                "java.lang.NullPointerException",
                AnalyzeInputType.JAVA_EXCEPTION,
                "stack trace",
                """
                        [Problem Type]
                        NullPointerException
                        [Suggestions]
                        1. Add a guard clause.
                        """);

        List<ProblemKnowledgeRecord> records = store.findRelevantRecords("add null checks for createOrder", target, 3);
        assertFalse(records.isEmpty());
        assertEquals("fix", records.get(0).source);
        assertTrue(records.stream().anyMatch(record -> "analyze".equals(record.source)));
    }

    @Test
    void shouldOnlyReturnSuccessfulRecordsForAiReuse(@TempDir Path tempDir) throws Exception {
        AppConfig config = loadConfig(tempDir);
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
                        """);
        store.recordFixExecution("fix createOrder null bug", target, false, "boom", "ai-fix/test");
        store.recordFixExecution("fix createOrder null bug", target, true, "", "ai-fix/test");

        List<ProblemKnowledgeRecord> records = store.findSuccessfulRelevantRecords("fix createOrder null bug", target, 5);
        assertEquals(1, records.size());
        assertEquals("success", records.get(0).status);
        assertEquals("fix", records.get(0).source);
    }

    @Test
    void shouldEnrichSuccessfulFixExecutionWithPatchInsight(@TempDir Path tempDir) throws Exception {
        AppConfig config = loadConfig(tempDir);
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        MethodInfo target = new MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.className = "OrderService";
        target.methodName = "createOrder";

        Path patchFile = tempDir.resolve("create-order.diff");
        Files.writeString(
                patchFile,
                """
                        diff --git a/src/main/java/demo/OrderService.java b/src/main/java/demo/OrderService.java
                        --- a/src/main/java/demo/OrderService.java
                        +++ b/src/main/java/demo/OrderService.java
                        @@ -10,3 +10,6 @@
                        +if (payment == null) {
                        +    return;
                        +}
                        -run();
                        +run();
                        """,
                StandardCharsets.UTF_8);

        store.recordFixExecution("fix createOrder null bug", target, true, "", "ai-fix/test", patchFile.toString());

        Path jsonl = config.getKnowledgeDirectory().resolve("records.jsonl");
        String line = Files.readString(jsonl, StandardCharsets.UTF_8).trim();
        Map<?, ?> record = new ObjectMapper().readValue(line, Map.class);
        assertTrue(String.valueOf(record.get("analysis")).contains("Patch applied successfully"));
        assertTrue(String.valueOf(record.get("repairScheme")).contains("if (payment == null)"));
        Map<?, ?> metadata = (Map<?, ?>) record.get("metadata");
        assertEquals("null-guard", metadata.get("repairPattern"));
        assertEquals(4, metadata.get("addedLines"));
        assertEquals(1, metadata.get("removedLines"));
        assertEquals(1, metadata.get("hunkCount"));
    }

    private AppConfig loadConfig(Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "app.home=" + tempDir.resolve("app-home").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        return AppConfig.load(configFile.toString());
    }
}
