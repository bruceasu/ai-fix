package me.asu.ai.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.asu.ai.config.AppConfig;
import org.junit.jupiter.api.Test;

class FixCliOptionsTest {

    @Test
    void parseShouldReadExplicitArguments() {
        AppConfig config = AppConfig.load("missing-test-config.properties");
        String[] args = {
                "--task", "refine retry",
                "--match", "order service",
                "--method", "createOrder",
                "--class", "OrderService",
                "--limit", "5",
                "--page-size", "12",
                "--preview-lines", "8",
                "--provider", "groq",
                "--model", "llama-x",
                "--project-summary", "project-summary.json",
                "--dry-run",
                "--no-select",
                "--print-config",
                "--stash"
        };

        FixCliOptions options = FixCliOptions.parse(args, config);

        assertEquals("refine retry", options.task);
        assertEquals("order service", options.matchQuery.trim());
        assertEquals("createOrder", options.methodName);
        assertEquals("OrderService", options.classFilter);
        assertEquals(5, options.limit);
        assertEquals(12, options.pageSize);
        assertEquals(8, options.previewLines);
        assertEquals("groq", options.provider);
        assertEquals("llama-x", options.model);
        assertEquals("project-summary.json", options.projectSummaryPath);
        assertTrue(options.dryRun);
        assertTrue(options.noSelect);
        assertTrue(options.printConfigOnly);
        assertTrue(options.useStash);
    }

    @Test
    void normalizeShouldTrimQueryAndRestoreInvalidNumbers() {
        FixCliOptions options = new FixCliOptions();
        options.matchQuery = "  retry order  ";
        options.pageSize = 0;
        options.previewLines = -3;

        options.normalize();

        assertEquals("retry order", options.matchQuery);
        assertEquals(FixCliOptions.DEFAULT_INTERACTIVE_PAGE_SIZE, options.pageSize);
        assertEquals(FixCliOptions.DEFAULT_PREVIEW_LINES, options.previewLines);
    }

    @Test
    void parseShouldTreatUnknownTokensAsMatchTerms() {
        AppConfig config = AppConfig.load("missing-test-config.properties");
        String[] args = {"retry", "service", "--task", "x"};

        FixCliOptions options = FixCliOptions.parse(args, config);

        assertEquals("retry service", options.matchQuery.trim());
        assertFalse(options.dryRun);
    }
}
