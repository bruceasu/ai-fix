package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateExeCliTest {

    @Test
    void shouldPrintUsage() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            GenerateExeCli.printUsage();
        } finally {
            System.setOut(original);
        }
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("generate-exe"));
        assertTrue(text.contains("--skill"));
        assertTrue(text.contains("--output"));
        assertTrue(text.contains("manual override"));
    }

    @Test
    void shouldExportWorkspaceSkillAsWorkflowPackage(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                """
                        tools.dir=%s
                        skills.dir=%s
                        """.formatted(
                        Path.of("workspace", "tools").toAbsolutePath().normalize().toString().replace("\\", "/"),
                        Path.of("workspace", "skills").toAbsolutePath().normalize().toString().replace("\\", "/")),
                StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("exported-skill");

        GenerateExeCli.main(new String[] {
                "--skill", "generate-import-control-and-run",
                "--output", outputDir.toString(),
                "--config", configFile.toString()
        });

        assertTrue(Files.isRegularFile(outputDir.resolve("workflow.json")));
        assertTrue(Files.isRegularFile(outputDir.resolve("main.py")));
        assertTrue(!Files.exists(outputDir.resolve("main.go")));
        assertTrue(Files.isDirectory(outputDir.resolve("tools").resolve("import-control-file-generator")));
        assertTrue(Files.isDirectory(outputDir.resolve("tools").resolve("import-tool")));
        String manifest = Files.readString(outputDir.resolve("workflow.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("generate-import-control-and-run"));
        assertTrue(manifest.contains("import-control-file-generator"));
        assertTrue(manifest.contains("import-tool"));
        assertTrue(manifest.contains("\"executionMode\" : \"external-tool\""));
    }

    @Test
    void shouldExportBuiltinLlmSkillAsManualWorkflow(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("exported-ai-skill");

        GenerateExeCli.main(new String[] {
                "--skill", "analyze-java-error",
                "--output", outputDir.toString()
        });

        assertTrue(Files.isRegularFile(outputDir.resolve("workflow.json")));
        assertTrue(Files.isRegularFile(outputDir.resolve("main.py")));
        String manifest = Files.readString(outputDir.resolve("workflow.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("analyze-java-error"));
        assertTrue(manifest.contains("\"executionMode\" : \"manual-tool\""));
        assertTrue(manifest.contains("\"manualSteps\""));
    }

    @Test
    void shouldExportBuiltinToolSkillAsManualAndAiWorkflow(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("exported-mixed-skill");

        GenerateExeCli.main(new String[] {
                "--skill", "explain-symbol",
                "--output", outputDir.toString()
        });

        assertTrue(Files.isRegularFile(outputDir.resolve("workflow.json")));
        assertTrue(Files.isRegularFile(outputDir.resolve("main.py")));
        String manifest = Files.readString(outputDir.resolve("workflow.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("explain-symbol"));
        assertTrue(manifest.contains("\"executionMode\" : \"manual-tool\""));
    }
}
