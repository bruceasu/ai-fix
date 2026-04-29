package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AiFixCLITest {

    @Test
    void helpFlagShouldBeDetected() throws Exception {
        assertTrue(invokeContainsHelpFlag("--help"));
        assertTrue(invokeContainsHelpFlag("-h"));
        assertFalse(invokeContainsHelpFlag("--task", "refine retry"));
    }

    @Test
    void findOptionValueShouldReturnConfiguredPath() throws Exception {
        assertEquals("conf/custom.properties",
                invokeFindOptionValue(new String[]{"--task", "x", "--config", "conf/custom.properties"}, "--config"));
        assertEquals(null, invokeFindOptionValue(new String[]{"--task", "x"}, "--config"));
    }

    @Test
    void defaultCommitMessageShouldPrefixTask() {
        assertEquals("AI fix: tighten null handling", AiFixCLI.buildDefaultCommitMessage("tighten null handling"));
    }

    @Test
    void defaultBranchNameShouldUseAiFixPrefix() {
        String branch = AiFixCLI.defaultBranchName();
        assertTrue(branch.startsWith("ai-fix/"));
        assertEquals("ai-fix/".length() + 14, branch.length());
    }

    private boolean invokeContainsHelpFlag(String... args) throws Exception {
        Method method = AiFixCLI.class.getDeclaredMethod("containsHelpFlag", String[].class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, (Object) args);
    }

    private String invokeFindOptionValue(String[] args, String optionName) throws Exception {
        Method method = AiFixCLI.class.getDeclaredMethod("findOptionValue", String[].class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, args, optionName);
    }
}
