package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.asu.ai.config.AppConfig;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolExecutorTest {

    @Test
    void shouldExecuteBuiltinEchoTool() {
        ToolExecutor executor = new ToolExecutor(new ToolCatalogService());
        ToolExecutionResult result = executor.execute("echo-tool", "{\"text\":\"world\",\"prefix\":\"hello \"}", true);

        assertTrue(result.ok());
        assertEquals("hello world", result.output());
        assertEquals("world", result.data().get("text"));
        assertEquals("hello ", result.data().get("prefix"));
    }

    @Test
    void shouldExecuteBuiltinLlmTool() {
        ToolExecutor executor = new FakeLlmToolExecutor();
        ToolExecutionResult result = executor.execute(
                "llm",
                "{\"prompt\":\"Summarize this text\",\"systemPrompt\":\"You are a concise summarizer.\",\"provider\":\"groq\",\"model\":\"llama-test\"}",
                true);

        assertTrue(result.ok());
        assertEquals("fake-llm-output", result.output());
        assertEquals("groq", result.data().get("provider"));
        assertEquals("llama-test", result.data().get("model"));
        assertEquals(true, result.data().get("hasSystemPrompt"));
    }

    @Test
    void shouldExecuteBuiltinLlmToolWithJsonFormat() {
        ToolExecutor executor = new FakeJsonLlmToolExecutor();
        ToolExecutionResult result = executor.execute(
                "llm",
                "{\"prompt\":\"Return JSON\",\"format\":\"json\"}",
                true);

        assertTrue(result.ok());
        assertEquals("json", result.data().get("format"));
        assertEquals("ok", ((java.util.Map<?, ?>) result.data().get("structured")).get("status"));
    }

    @Test
    void shouldValidateRequiredKeysForStructuredJson() {
        ToolExecutor executor = new FakeJsonLlmToolExecutor();
        ToolExecutionResult result = executor.execute(
                "llm",
                "{\"prompt\":\"Return JSON\",\"format\":\"json\",\"requiredKeys\":\"status,items\"}",
                true);

        assertTrue(result.ok());
        assertEquals(java.util.List.of("status", "items"), result.data().get("requiredKeys"));
    }

    @Test
    void shouldExecuteBuiltinLlmToolWithYamlFormat() {
        ToolExecutor executor = new FakeYamlLlmToolExecutor();
        ToolExecutionResult result = executor.execute(
                "llm",
                "{\"prompt\":\"Return YAML\",\"format\":\"yaml\"}",
                true);

        assertTrue(result.ok());
        assertEquals("yaml", result.data().get("format"));
        assertEquals("tool", ((java.util.Map<?, ?>) result.data().get("structured")).get("action"));
    }

    @Test
    void shouldRejectUnsupportedLlmFormat() {
        ToolExecutor executor = new FakeLlmToolExecutor();
        ToolExecutionResult result = executor.execute(
                "llm",
                "{\"prompt\":\"Return XML\",\"format\":\"xml\"}",
                true);

        assertTrue(!result.ok());
        assertTrue(result.error().contains("Unsupported format"));
    }

    @Test
    void shouldFailWhenRequiredKeysAreMissing() {
        ToolExecutor executor = new FakeJsonLlmToolExecutor();
        ToolExecutionResult result = executor.execute(
                "llm",
                "{\"prompt\":\"Return JSON\",\"format\":\"json\",\"requiredKeys\":\"status,severity\"}",
                true);

        assertTrue(!result.ok());
        assertTrue(result.error().contains("Missing required keys"));
        assertTrue(result.error().contains("severity"));
    }

    @Test
    void shouldReadProjectSummaryFile(@TempDir Path tempDir) throws IOException {
        Path summaryFile = tempDir.resolve("project-summary.json");
        Files.writeString(summaryFile, "{\"projectName\":\"demo\"}", StandardCharsets.UTF_8);

        ToolExecutor executor = new ToolExecutor(new ToolCatalogService());
        ToolExecutionResult result = executor.execute(
                "project-summary-reader",
                "{\"path\":\"" + escape(summaryFile.toString()) + "\"}",
                true);

        assertTrue(result.ok());
        assertEquals("{\"projectName\":\"demo\"}", result.output());
    }

    @Test
    void shouldReadLocalFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "hello read-file", StandardCharsets.UTF_8);

        ToolExecutor executor = new ToolExecutor(new ToolCatalogService());
        ToolExecutionResult result = executor.execute(
                "read-file",
                "{\"path\":\"" + escape(file.toString()) + "\",\"maxChars\":100}",
                true);

        assertTrue(result.ok());
        assertEquals("hello read-file", result.output());
        assertEquals(file.toString().replace("\\", "/"), result.data().get("path"));
    }

    @Test
    void shouldUseInjectedWebSearchSupport() {
        ToolExecutor executor = new ToolExecutor(new ToolCatalogService(), new FakeWebToolSupport());
        ToolExecutionResult result = executor.execute(
                "web-search",
                "{\"query\":\"spring retry\",\"site\":\"docs.spring.io\",\"limit\":3}",
                true);

        assertTrue(result.ok());
        assertEquals("search:spring retry|docs.spring.io|3", result.output());
    }

    @Test
    void shouldUseInjectedWebFetchSupport() {
        ToolExecutor executor = new ToolExecutor(new ToolCatalogService(), new FakeWebToolSupport());
        ToolExecutionResult result = executor.execute(
                "web-fetch",
                "{\"url\":\"https://example.com/doc\",\"maxChars\":500}",
                true);

        assertTrue(result.ok());
        assertEquals("fetch:https://example.com/doc|500", result.output());
    }

    @Test
    void shouldUseInjectedGitDiffSupport() {
        ToolExecutor executor = new ToolExecutor(new ToolCatalogService(), new FakeWebToolSupport(), new FakeGitDiffSupport());
        ToolExecutionResult result = executor.execute("git-diff-reader", "{}", true);

        assertTrue(result.ok());
        assertEquals("fake diff content", result.output());
        assertEquals("git diff HEAD --", result.data().get("source"));
    }

    @Test
    void shouldListFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("dir"));
        Files.writeString(tempDir.resolve("dir").resolve("b.java"), "b", StandardCharsets.UTF_8);

        ToolExecutor executor = new ToolExecutor(new ToolCatalogService());
        ToolExecutionResult result = executor.execute(
                "list-files",
                "{\"root\":\"" + escape(tempDir.toString()) + "\",\"glob\":\"\",\"limit\":10}",
                true);

        assertTrue(result.ok());
        assertTrue(result.output().contains("a.txt"));
        assertTrue(result.output().contains("dir/b.java"));
        assertTrue(result.data().containsKey("files"));
    }

    @Test
    void shouldUseInjectedProjectIndexSupport() {
        ToolExecutor executor = new ToolExecutor(
                new ToolCatalogService(),
                new FakeWebToolSupport(),
                new FakeGitDiffSupport(),
                new FakeProjectIndexSupport(),
                new FakeCommandBridgeSupport());
        ToolExecutionResult result = executor.execute(
                "index-symbol-reader",
                "{\"symbol\":\"createOrder\",\"container\":\"OrderService\",\"indexPath\":\"index.json\",\"limit\":3}",
                true);

        assertTrue(result.ok());
        assertEquals("fake symbol context", result.output());
        assertEquals("target", result.data().get("matchType"));
    }

    @Test
    void shouldUseInjectedCommandAnalyzeBridge() {
        ToolExecutor executor = new ToolExecutor(
                new ToolCatalogService(),
                new FakeWebToolSupport(),
                new FakeGitDiffSupport(),
                new FakeProjectIndexSupport(),
                new FakeCommandBridgeSupport());
        ToolExecutionResult result = executor.execute(
                "command-analyze",
                "{\"input\":\"java.lang.NullPointerException\"}",
                true);

        assertTrue(result.ok());
        assertEquals("fake analyze output", result.output());
        assertEquals("analyze", result.data().get("command"));
    }

    @Test
    void commandFixSuggestShouldReturnCandidatesWhenAmbiguous(@TempDir Path tempDir) throws IOException {
        Path indexFile = tempDir.resolve("index.json");
        MethodInfo first = new MethodInfo();
        first.className = "OrderService";
        first.methodName = "createOrder";
        first.file = "OrderService.java";
        first.beginLine = 10;
        first.endLine = 20;

        MethodInfo second = new MethodInfo();
        second.className = "LegacyOrderService";
        second.methodName = "createOrder";
        second.file = "LegacyOrderService.java";
        second.beginLine = 30;
        second.endLine = 40;

        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(indexFile.toFile(), java.util.List.of(first, second));

        ToolExecutor executor = new ToolExecutor(new ToolCatalogService());
        ToolExecutionResult result = executor.execute(
                "command-fix-suggest",
                "{\"task\":\"add null checks\",\"symbol\":\"createOrder\",\"indexPath\":\"" + escape(indexFile.toString()) + "\",\"limit\":2}",
                true);

        assertTrue(result.ok());
        assertTrue(result.output().contains("[candidates]"));
        assertEquals("candidates", result.data().get("matchType"));
    }

    @Test
    void shouldExecuteExternalCommandTool(@TempDir Path tempDir) throws IOException {
        Path toolsDir = tempDir.resolve("tools").resolve("demo-external");
        Files.createDirectories(toolsDir);
        Files.writeString(
                toolsDir.resolve("tool.yaml"),
                """
                        name: demo-external
                        description: Demo external tool.
                        autoExecuteAllowed: true
                        tool:
                          type: external-command
                          program: python
                          args:
                            - ./run.py
                            - --name
                            - ${name}
                        arguments:
                          - name: name
                            type: string
                            required: false
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                toolsDir.resolve("run.py"),
                """
                        import argparse
                        parser = argparse.ArgumentParser()
                        parser.add_argument("--name", default="")
                        args = parser.parse_args()
                        print("hello:" + args.name)
                        """,
                StandardCharsets.UTF_8);

        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "tools.dir=" + escape(tempDir.resolve("tools").toString()),
                StandardCharsets.UTF_8);
        ToolExecutor executor = new ToolExecutor(new ToolCatalogService(AppConfig.load(configFile.toString())));

        ToolExecutionResult result = executor.execute("demo-external", "{\"name\":\"world\"}", true);

        assertTrue(result.ok());
        assertEquals("hello:world", result.output());
        assertEquals("external-command", result.data().get("type"));
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\");
    }

    private static final class FakeWebToolSupport extends WebToolSupport {
        @Override
        public String fetch(String url, int maxChars) {
            return "fetch:" + url + "|" + maxChars;
        }

        @Override
        public String search(String query, String site, int limit) {
            return "search:" + query + "|" + site + "|" + limit;
        }
    }

    private static final class FakeGitDiffSupport extends GitDiffSupport {
        @Override
        public String readChangedFilesReviewInput() {
            return "fake diff content";
        }
    }

    private static final class FakeProjectIndexSupport extends ProjectIndexSupport {
        @Override
        public String explainSymbolInput(String indexPath, String symbol, String container, int limit) {
            return "fake symbol context";
        }
    }

    private static final class FakeCommandBridgeSupport extends CommandBridgeSupport {
        private FakeCommandBridgeSupport() {
            super(null);
        }

        @Override
        public ToolExecutionResult executeAnalyze(String toolName, com.fasterxml.jackson.databind.JsonNode args) {
            return ToolExecutionResult.success(toolName, "fake analyze output", java.util.Map.of("command", "analyze"));
        }
    }

    private static final class FakeLlmToolExecutor extends ToolExecutor {
        FakeLlmToolExecutor() {
            super(new ToolCatalogService());
        }

        @Override
        protected me.asu.ai.llm.LLMClient createLlmClient(String provider, String model) {
            return prompt -> "fake-llm-output";
        }
    }

    private static final class FakeJsonLlmToolExecutor extends ToolExecutor {
        FakeJsonLlmToolExecutor() {
            super(new ToolCatalogService());
        }

        @Override
        protected me.asu.ai.llm.LLMClient createLlmClient(String provider, String model) {
            return prompt -> """
                    ```json
                    {"status":"ok","items":[1,2]}
                    ```
                    """;
        }
    }

    private static final class FakeYamlLlmToolExecutor extends ToolExecutor {
        FakeYamlLlmToolExecutor() {
            super(new ToolCatalogService());
        }

        @Override
        protected me.asu.ai.llm.LLMClient createLlmClient(String provider, String model) {
            return prompt -> """
                    ```yaml
                    action: tool
                    name: read-file
                    ```
                    """;
        }
    }
}
