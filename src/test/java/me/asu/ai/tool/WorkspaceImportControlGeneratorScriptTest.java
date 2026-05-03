package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceImportControlGeneratorScriptTest {

    @Test
    void wrapperShouldPrintOnlyGeneratedPath(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "python launcher is required");

        Path toolDir = tempDir.resolve("import-control-file-generator");
        Path importToolDir = tempDir.resolve("import-tool");
        Files.createDirectories(toolDir);
        Files.createDirectories(importToolDir);

        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/import-control-file-generator/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        Files.writeString(
                importToolDir.resolve("generate_control_file.py"),
                """
                        import sys
                        output = sys.argv[sys.argv.index("--output") + 1]
                        print("Generated control file: " + output)
                        """,
                StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "--csv-path",
                "D:/exports/demo.csv",
                "--import-date",
                "2026-05-03",
                "--output",
                "D:/exports/demo.csv.control.yaml");
        builder.directory(toolDir.toFile());
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, error);
        assertEquals("D:/exports/demo.csv.control.yaml", output);
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
