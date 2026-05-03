package me.asu.ai.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnalyzeInputClassifierTest {

    @Test
    void shouldClassifyJavaExceptionDeterministically() {
        String input = """
                java.lang.NullPointerException: x
                    at me.asu.demo.OrderService.create(OrderService.java:42)
                """;
        assertEquals(AnalyzeInputType.JAVA_EXCEPTION, AnalyzeInputClassifier.classify(input));
    }

    @Test
    void shouldClassifyTestOutputBeforeGenericLog() {
        String input = """
                [INFO] Tests run: 3, Failures: 1, Errors: 0
                java.lang.AssertionError: expected:<1> but was:<2>
                """;
        assertEquals(AnalyzeInputType.TEST_OUTPUT, AnalyzeInputClassifier.classify(input));
    }

    @Test
    void shouldFallbackToLog() {
        String input = "2026-05-03 12:00:01 INFO boot completed";
        assertEquals(AnalyzeInputType.LOG, AnalyzeInputClassifier.classify(input));
    }
}
