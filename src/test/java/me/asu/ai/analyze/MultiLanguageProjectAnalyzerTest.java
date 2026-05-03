package me.asu.ai.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.asu.ai.model.ProjectSummary;
import org.junit.jupiter.api.Test;

class MultiLanguageProjectAnalyzerTest {

    @Test
    void shouldAnalyzeGoProject() throws Exception {
        Path root = Files.createTempDirectory("go-analyze");
        Files.writeString(root.resolve("go.mod"), "module example.com/demo", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("main.go"), """
                package main

                import "net/http"

                func main() {}
                """, StandardCharsets.UTF_8);

        ProjectSummary summary = new GoProjectAnalyzer().analyze(root);

        assertEquals("go", summary.primaryLanguage);
        assertEquals(1, summary.goFileCount);
        assertFalse(summary.entryPoints.isEmpty());
    }

    @Test
    void shouldAnalyzePythonProject() throws Exception {
        Path root = Files.createTempDirectory("py-analyze");
        Files.writeString(root.resolve("app.py"), """
                import requests

                class App:
                    pass

                def main():
                    pass

                if __name__ == "__main__":
                    main()
                """, StandardCharsets.UTF_8);

        ProjectSummary summary = new PythonProjectAnalyzer().analyze(root);

        assertEquals("python", summary.primaryLanguage);
        assertEquals(1, summary.pythonFileCount);
        assertFalse(summary.entryPoints.isEmpty());
    }
}
