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

class WorkspaceYoutubeTranscriptToolScriptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dryRunShouldPrintResolvedPlan(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "python launcher is required");

        Path toolDir = tempDir.resolve("youtube-transcript");
        Files.createDirectories(toolDir);
        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/youtube-transcript/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        Files.writeString(
                toolDir.resolve("youtube_transcript_lib.py"),
                Files.readString(Path.of("workspace/tools/youtube-transcript/youtube_transcript_lib.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "--input",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "--languages",
                "en,ja",
                "--output-mode",
                "plain-text",
                "--dry-run",
                "true");
        builder.directory(toolDir.toFile());
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, error);
        JsonNode json = mapper.readTree(output);
        assertEquals("youtube-transcript", json.path("tool").asText());
        assertEquals(1, json.path("rawInputs").size());
        assertEquals("en", json.path("languages").get(0).asText());
        assertEquals("ja", json.path("languages").get(1).asText());
        assertTrue(json.path("dryRun").asBoolean());
        assertEquals("plain-text", json.path("outputMode").asText());
        assertTrue(json.path("includeTranscriptText").asBoolean());
        assertEquals(12000, json.path("maxCharsPerTranscript").asInt());
        assertTrue(json.path("outputDir").asText().endsWith("output"));
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
