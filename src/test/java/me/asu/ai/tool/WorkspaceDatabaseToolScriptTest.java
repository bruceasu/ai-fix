package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceDatabaseToolScriptTest {

    @Test
    void mysqlScriptShouldDefaultToLocalMyCnf(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "python launcher is required");

        Path toolDir = tempDir.resolve("mysql-client");
        Files.createDirectories(toolDir);
        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/mysql-client/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        Files.writeString(
                toolDir.resolve("my.cnf"),
                """
                        [client]
                        host=localhost
                        """,
                StandardCharsets.UTF_8);

        Path fakeBin = tempDir.resolve("fake-bin");
        Files.createDirectories(fakeBin);
        Path mysqlProgram = createFakeEchoProgram(fakeBin, "mysql", "MYSQL_DEFAULTS_FILE");

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "-Query",
                "SELECT 1");
        builder.directory(toolDir.toFile());
        builder.environment().put("PATH", fakeBin + pathSeparator() + builder.environment().getOrDefault("PATH", ""));
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();

        assertEquals(0, exit);
        assertEquals(toolDir.resolve("my.cnf").toString(), output);
        Files.deleteIfExists(mysqlProgram);
    }

    @Test
    void pgsqlScriptShouldDefaultToLocalPgpass(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isPythonAvailable(), "python launcher is required");

        Path toolDir = tempDir.resolve("pgsql-client");
        Files.createDirectories(toolDir);
        Files.writeString(
                toolDir.resolve("run.py"),
                Files.readString(Path.of("workspace/tools/pgsql-client/run.py"), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        Files.writeString(
                toolDir.resolve("pgpass.conf"),
                "localhost:5432:*:demo:secret",
                StandardCharsets.UTF_8);

        Path fakeBin = tempDir.resolve("fake-bin");
        Files.createDirectories(fakeBin);
        Path psqlProgram = createFakeEchoProgram(fakeBin, "psql", "PGPASSFILE");

        ProcessBuilder builder = new ProcessBuilder(
                "python",
                toolDir.resolve("run.py").toString(),
                "-Query",
                "SELECT 1");
        builder.directory(toolDir.toFile());
        builder.environment().put("PATH", fakeBin + pathSeparator() + builder.environment().getOrDefault("PATH", ""));
        Process process = builder.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();

        assertEquals(0, exit);
        assertEquals(toolDir.resolve("pgpass.conf").toString(), output);
        Files.deleteIfExists(psqlProgram);
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

    private Path createFakeEchoProgram(Path dir, String baseName, String envKey) throws IOException {
        if (isWindows()) {
            Path script = dir.resolve(baseName + ".cmd");
            Files.writeString(
                    script,
                    "@echo off\r\necho %" + envKey + "%\r\n",
                    StandardCharsets.UTF_8);
            return script;
        }
        Path script = dir.resolve(baseName);
        Files.writeString(
                script,
                "#!/bin/sh\nprintenv " + envKey + "\n",
                StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }

    private boolean isWindows() {
        return java.io.File.separatorChar == '\\';
    }

    private String pathSeparator() {
        return java.io.File.pathSeparator;
    }
}
