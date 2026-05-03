package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCliTest {

    @Test
    void shouldPrintDescribeUsage() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ToolCli.printUsage();
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("tool describe --name <toolName>"));
    }

    @Test
    void shouldDescribeExternalTool(@TempDir Path tempDir) throws Exception {
        Path toolDir = tempDir.resolve("tools").resolve("demo-tool");
        Files.createDirectories(toolDir);
        Files.writeString(
                toolDir.resolve("tool.yaml"),
                """
                        name: demo-tool
                        description: Demo external tool.
                        autoExecuteAllowed: true
                        tool:
                          type: external-command
                          program: python
                          args:
                            - ./run.py
                            - --name
                            - ${name}
                        arguments:
                          - name: name
                            type: string
                            description: Demo argument
                            required: false
                            defaultValue: world
                        """,
                StandardCharsets.UTF_8);
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "tools.dir=" + toolDir.getParent().toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ToolCli.main(new String[] {"describe", "--name", "demo-tool", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Name: demo-tool"));
        assertTrue(text.contains("Program: python"));
        assertTrue(text.contains("Demo argument"));
        assertTrue(text.contains("default=world"));
    }

    @Test
    void shouldDescribeWorkspaceImportControlGenerator(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "tools.dir=" + Path.of("workspace", "tools").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ToolCli.main(new String[] {"describe", "--name", "import-control-file-generator", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Name: import-control-file-generator"));
        assertTrue(text.contains("csvPath"));
        assertTrue(text.contains("Program: python"));
        assertTrue(text.contains("./run.py"));
    }

    @Test
    void shouldDescribeWorkspaceYoutubeTranscriptTool(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "tools.dir=" + Path.of("workspace", "tools").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ToolCli.main(new String[] {"describe", "--name", "youtube-transcript", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Name: youtube-transcript"));
        assertTrue(text.contains("Program: python"));
        assertTrue(text.contains("languages"));
        assertTrue(text.contains("outputDir"));
        assertTrue(text.contains("dryRun"));
    }

    @Test
    void shouldDescribeWorkspacePlaywrightCliTool(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "tools.dir=" + Path.of("workspace", "tools").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ToolCli.main(new String[] {"describe", "--name", "playwright-cli", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Name: playwright-cli"));
        assertTrue(text.contains("Program: python"));
        assertTrue(text.contains("action"));
        assertTrue(text.contains("dryRun"));
    }
}
