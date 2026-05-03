package me.asu.ai.fix;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixSuggestionGeneratorTest {

    @Test
    void generateShouldReturnStructuredSuggestion() throws Exception {
        MethodInfo target = createTarget();
        LLMClient llm = prompt -> """
                [Mode]
                suggestion-only
                [Target]
                OrderService#createOrder
                [Understanding]
                Add a null guard before invoking the dependency.
                [Suggestions]
                1. Add a null check before the call.
                2. Preserve the current call order.
                [Sample Code]
                ```java
                if (payment == null) {
                    return;
                }
                ```
                [Cautions]
                1. Do not expand the change scope.
                2. Keep compatibility.
                """;

        FixSuggestionGenerator generator = new FixSuggestionGenerator(llm, AppConfig.load(), null);
        String result = generator.generate("add null guard", target);

        assertTrue(result.contains("[Mode]"));
        assertTrue(result.contains("[Suggestions]"));
        assertTrue(result.contains("OrderService#createOrder"));
    }

    @Test
    void generateShouldUseLocalFallbackWhenLlmFails() throws Exception {
        MethodInfo target = createTarget();
        LLMClient llm = prompt -> {
            throw new RuntimeException("llm unavailable");
        };

        FixSuggestionGenerator generator = new FixSuggestionGenerator(llm, AppConfig.load(), null);
        String result = generator.generate("add null guard for nullable payment", target);

        assertTrue(result.contains("[Mode]"));
        assertTrue(result.contains("Local suggestion fallback mode"));
        assertTrue(result.contains("null checks"));
    }

    @Test
    void generateShouldIncludeRelevantKnowledgeInPrompt(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "app.home=" + tempDir.resolve("app-home").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        Path patchFile = tempDir.resolve("fix.diff");
        Files.writeString(
                patchFile,
                """
                        diff --git a/src/main/java/example/OrderService.java b/src/main/java/example/OrderService.java
                        --- a/src/main/java/example/OrderService.java
                        +++ b/src/main/java/example/OrderService.java
                        @@ -10,3 +10,6 @@
                        +if (payment == null) {
                        +    return;
                        +}
                        -run();
                        +run();
                        """,
                StandardCharsets.UTF_8);

        MethodInfo target = createTarget();
        target.language = "java";
        target.containerName = "example.OrderService";
        target.symbolName = "createOrder";

        ProblemKnowledgeStore knowledgeStore = new ProblemKnowledgeStore(config);
        knowledgeStore.recordFixExecution("add null guard", target, true, "", "ai-fix/test", patchFile.toString());

        final String[] capturedPrompt = new String[1];
        LLMClient llm = prompt -> {
            capturedPrompt[0] = prompt;
            return """
                    [Mode]
                    suggestion-only
                    [Target]
                    OrderService#createOrder
                    [Understanding]
                    Add a null guard before invoking the dependency.
                    [Suggestions]
                    1. Add a null check before the call.
                    2. Preserve the current call order.
                    [Sample Code]
                    ```java
                    if (payment == null) {
                        return;
                    }
                    ```
                    [Cautions]
                    1. Do not expand the change scope.
                    2. Keep compatibility.
                    """;
        };

        FixSuggestionGenerator generator = new FixSuggestionGenerator(llm, config, null, knowledgeStore);
        generator.generate("add null guard", target);

        assertTrue(capturedPrompt[0].contains("Relevant historical repair knowledge:"));
        assertTrue(capturedPrompt[0].contains("status=success"));
        assertTrue(capturedPrompt[0].contains("example.OrderService#createOrder"));
        assertTrue(capturedPrompt[0].contains("pattern=null-guard"));
    }

    private MethodInfo createTarget() throws Exception {
        Path projectRoot = Files.createTempDirectory("fix-suggestion-project");
        Path sourceFile = projectRoot.resolve("src/main/java/example/OrderService.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package example;

                class OrderService {
                    void createOrder() {
                        run();
                    }
                }
                """, StandardCharsets.UTF_8);

        MethodInfo target = new MethodInfo();
        target.projectRoot = projectRoot.toString();
        target.file = "src/main/java/example/OrderService.java";
        target.className = "OrderService";
        target.methodName = "createOrder";
        target.signature = "void createOrder()";
        target.beginLine = 4;
        target.endLine = 6;
        return target;
    }
}
