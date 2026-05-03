package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacePlaywrightCliToolScriptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dryRunShouldPrintResolvedPlan(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "python launcher is required");

        Path toolDir = tempDir.resolve("playwright-cli");
        Files.createDirectories(toolDir);
        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/playwright-cli/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "--action",
                "open",
                "--url",
                "https://example.com",
                "--session",
                "demo",
                "--dry-run",
                "true");
        builder.directory(toolDir.toFile());
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, error);
        JsonNode json = mapper.readTree(output);
        assertEquals("playwright-cli", json.path("tool").asText());
        assertEquals("open", json.path("action").asText());
        assertEquals("demo", json.path("session").asText());
        assertEquals(true, json.path("dryRun").asBoolean());
    }

    @Test
    void shouldFailWithInstallHintWhenPlaywrightCliIsMissing(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "python launcher is required");

        Path toolDir = tempDir.resolve("playwright-cli");
        Files.createDirectories(toolDir);
        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/playwright-cli/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "--action",
                "open",
                "--url",
                "https://example.com");
        builder.directory(toolDir.toFile());
        builder.environment().put("PATH", tempDir.resolve("missing-bin").toString());
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertTrue(exitCode != 0);
        String combined = output + "\n" + error;
        assertTrue(combined.contains("playwright-cli was not found in PATH"));
        assertTrue(combined.contains("npm install -g @playwright/cli@latest"));
    }

    private boolean isPythonAvailable() {
        try {
            Process process = new ProcessBuilder("python", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
