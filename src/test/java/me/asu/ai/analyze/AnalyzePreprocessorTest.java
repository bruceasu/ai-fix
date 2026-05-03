package me.asu.ai.analyze;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnalyzePreprocessorTest {

    @Test
    void shouldKeepImportantLinesAndDropDebugNoise() {
        String input = """
                2026-05-03 10:00:00 DEBUG request started
                java.lang.IllegalStateException: bad state
                    at me.asu.demo.Service.run(Service.java:12)
                    at me.asu.demo.Service.run(Service.java:12)
                2026-05-03 10:00:01 ERROR failed
                """;

        String processed = AnalyzePreprocessor.process(input, AnalyzeInputType.JAVA_EXCEPTION);

        assertTrue(processed.contains("IllegalStateException"));
        assertTrue(processed.contains("Service.java:12"));
        assertTrue(processed.contains("ERROR failed"));
        assertFalse(processed.contains("DEBUG request started"));
        assertTrue(processed.indexOf("Service.java:12") == processed.lastIndexOf("Service.java:12"));
    }
}
