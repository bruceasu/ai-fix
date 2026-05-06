package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import me.asu.ai.util.Utils;

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
        assertEquals("AI fix: tighten null handling", AiFixCli.buildDefaultCommitMessage("tighten null handling"));
    }

    @Test
    void defaultBranchNameShouldUseAiFixPrefix() {
        String branch = AiFixCli.defaultBranchName();
        assertTrue(branch.startsWith("ai-fix/"));
        assertEquals("ai-fix/".length() + 14, branch.length());
    }

    private boolean invokeContainsHelpFlag(String... args) throws Exception {
        return Utils.containsHelpFlag(args);
    }

    private String invokeFindOptionValue(String[] args, String optionName) throws Exception {
        return Utils.findOptionValue(args, optionName);
    }
}
