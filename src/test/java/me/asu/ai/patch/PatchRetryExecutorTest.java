package me.asu.ai.patch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.asu.ai.config.AppConfig;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PatchRetryExecutorTest {
    private String originalUserHome;

    @Test
    void initialPromptShouldIncludeAgentInstructions() throws Exception {
        useTemporaryUserHomeWithAifix("""
                PATCH-FIRST
                Output ONLY unified diff.
                """);
        PatchRetryExecutor executor = newExecutor();
        MethodInfo target = sampleTarget();

        Method method = PatchRetryExecutor.class
                .getDeclaredMethod("buildInitialPrompt", String.class, MethodInfo.class, String.class);
        method.setAccessible(true);

        String prompt = (String) method.invoke(executor, "add null checks", target, "void test() {}");

        assertTrue(prompt.contains("Base patch instructions:"));
        assertTrue(prompt.contains("PATCH-FIRST"));
        assertTrue(prompt.contains("Output ONLY unified diff."));
    }

    @Test
    void validateAndNormalizeDiffShouldAcceptPureUnifiedDiff() throws Exception {
        useTemporaryUserHomeWithAifix("");
        PatchRetryExecutor executor = newExecutor();
        Object result = invokeValidate(executor, """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@
                -old
                +new
                """);

        assertTrue(readBoolean(result, "valid"));
    }

    @Test
    void validateAndNormalizeDiffShouldRejectExplanationBeforeDiff() throws Exception {
        useTemporaryUserHomeWithAifix("");
        PatchRetryExecutor executor = newExecutor();
        Object result = invokeValidate(executor, """
                Here is the patch:
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@
                -old
                +new
                """);

        assertFalse(readBoolean(result, "valid"));
        assertTrue(readString(result, "error").contains("non-diff text"));
    }

    @Test
    void validateAndNormalizeDiffShouldAcceptFencedDiff() throws Exception {
        useTemporaryUserHomeWithAifix("");
        PatchRetryExecutor executor = newExecutor();
        Object result = invokeValidate(executor, """
                ```diff
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@
                -old
                +new
                ```
                """);

        assertTrue(readBoolean(result, "valid"));
    }

    @AfterEach
    void restoreUserHome() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
            originalUserHome = null;
        }
    }

    private PatchRetryExecutor newExecutor() {
        LLMClient llm = prompt -> "";
        return new PatchRetryExecutor(llm, "test-model", 2, true, AppConfig.load(), null);
    }

    private void useTemporaryUserHomeWithAifix(String content) throws IOException {
        if (originalUserHome == null) {
            originalUserHome = System.getProperty("user.home");
        }
        Path tempHome = Files.createTempDirectory("aifix-test-home");
        Path configDir = tempHome.resolve(".config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("aifix.md"), content, StandardCharsets.UTF_8);
        System.setProperty("user.home", tempHome.toString());
    }

    private MethodInfo sampleTarget() {
        MethodInfo target = new MethodInfo();
        target.file = "src/main/java/example/Test.java";
        target.className = "Test";
        target.methodName = "run";
        target.signature = "void run()";
        target.beginLine = 1;
        target.endLine = 3;
        return target;
    }

    private Object invokeValidate(PatchRetryExecutor executor, String text) throws Exception {
        Method method = PatchRetryExecutor.class
                .getDeclaredMethod("validateAndNormalizeDiff", String.class);
        method.setAccessible(true);
        return method.invoke(executor, text);
    }

    private boolean readBoolean(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(target);
    }

    private String readString(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (String) method.invoke(target);
    }
}
