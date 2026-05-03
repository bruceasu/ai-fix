package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AiAnalyzeCLITest {

    @Test
    void extractTextArgumentShouldIgnoreOptions() throws Exception {
        assertEquals(
                "java.lang.OutOfMemoryError",
                invokeExtractTextArgument(new String[]{"java.lang.OutOfMemoryError", "--provider", "openai"}));
    }

    @Test
    void extractTextArgumentShouldJoinPlainWords() throws Exception {
        assertEquals(
                "mvn test failed",
                invokeExtractTextArgument(new String[]{"mvn", "test", "failed"}));
    }

    @Test
    void usageShouldMentionAnalyzeOnlyBehavior() {
        String usage = captureUsage();
        assertTrue(usage.contains("this command only analyzes"));
        assertTrue(usage.contains("never edits code"));
    }

    private String invokeExtractTextArgument(String[] args) throws Exception {
        Method method = AiAnalyzeCLI.class.getDeclaredMethod("extractTextArgument", String[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, (Object) args);
    }

    private String captureUsage() {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        try {
            System.setOut(new java.io.PrintStream(output));
            AiAnalyzeCLI.printUsage();
            return output.toString();
        } finally {
            System.setOut(original);
        }
    }
}
