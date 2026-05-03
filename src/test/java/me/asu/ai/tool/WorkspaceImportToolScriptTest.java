package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceImportToolScriptTest {

    @Test
    void wrapperShouldPatchClientPathsBeforeDelegating(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable() && isPyYamlAvailable(), "python launcher with pyyaml is required");

        Path toolDir = tempDir.resolve("import-tool");
        Files.createDirectories(toolDir.resolve("bin"));
        Files.createDirectories(toolDir.resolve("config"));

        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/import-tool/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        Files.writeString(
                toolDir.resolve("config.yaml"),
                """
                        app:
                          base_dir: .
                        databases:
                          pg_local:
                            type: postgres
                            psql_path: psql
                            pgpass_file: ./config/pgpass.conf
                          mysql_local:
                            type: mysql
                            mysql_path: mysql
                            defaults_extra_file: ./config/mysql.cnf
                        jobs: []
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(toolDir.resolve("config").resolve("pgpass.conf"), "localhost:5432:*:demo:secret", StandardCharsets.UTF_8);
        Files.writeString(toolDir.resolve("config").resolve("mysql.cnf"), "[client]\npassword=secret\n", StandardCharsets.UTF_8);
        Files.writeString(
                toolDir.resolve("import_tool.py"),
                """
                        import sys
                        import yaml

                        config_path = sys.argv[sys.argv.index("--config") + 1]
                        command = "scan" if "scan" in sys.argv else "run-one"

                        with open(config_path, "r", encoding="utf-8") as handle:
                            cfg = yaml.safe_load(handle)
                        print(cfg["databases"]["pg_local"]["psql_path"])
                        print(cfg["databases"]["mysql_local"]["mysql_path"])
                        print(command)
                        """,
                StandardCharsets.UTF_8);

        Path fakePath = tempDir.resolve("fake-path");
        Files.createDirectories(fakePath);
        Path psql = createFakeCommand(fakePath, "psql");
        Path mysql = createFakeCommand(fakePath, "mysql");

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "--command",
                "scan",
                "--dry-run",
                "true");
        builder.directory(toolDir.toFile());
        builder.environment().put("PATH", fakePath + java.io.File.pathSeparator + builder.environment().getOrDefault("PATH", ""));
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, error);
        String[] lines = output.split("\\R");
        assertTrue(lines[0].contains("psql"));
        assertTrue(lines[1].contains("mysql"));
        assertEquals("scan", lines[2]);

        Files.deleteIfExists(psql);
        Files.deleteIfExists(mysql);
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

    private boolean isPyYamlAvailable() {
        try {
            Process process = new ProcessBuilder("python", "-c", "import yaml")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path createFakeCommand(Path dir, String baseName) throws Exception {
        if (java.io.File.separatorChar == '\\') {
            Path script = dir.resolve(baseName + ".cmd");
            Files.writeString(script, "@echo off\r\nexit /b 0\r\n", StandardCharsets.UTF_8);
            return script;
        }
        Path script = dir.resolve(baseName);
        Files.writeString(script, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }
}
