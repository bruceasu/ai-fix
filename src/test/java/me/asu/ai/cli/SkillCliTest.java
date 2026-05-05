package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.config.AppConfig;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillCliTest {

    @Test
    void shouldPrintUsageWithDescribeAndConvertMarkdown() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            SkillCli.printUsage();
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("skill describe --name <skillName>"));
        assertTrue(text.contains("skill convert-markdown --input <markdownFile> --output-dir <skillDir>"));
        assertTrue(text.contains("[--no-ai]"));
        assertTrue(text.contains("[--emit-yaml <path>]"));
    }

    @Test
    void shouldDescribeExternalSkillAndPreferMarkdown(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("skills").resolve("demo-skill");
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("skill.yaml"),
                """
                        name: demo-skill
                        description: Demo external skill.
                        autoExecuteAllowed: false
                        arguments:
                          - name: query
                            type: string
                            description: Query text
                            required: false
                        steps:
                          - id: fetch
                            type: tool
                            toolName: web-fetch
                            outputKey: webContent
                            arguments:
                              url: ${query}
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                        # Demo Skill

                        This markdown description should be preferred in skill describe output.
                        """,
                StandardCharsets.UTF_8);
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "skills.dir=" + skillDir.getParent().toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            SkillCli.main(new String[] {"describe", "--name", "demo-skill", "--config", configFile.toString()});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Name: demo-skill"));
        assertTrue(text.contains("Documentation:"));
        assertTrue(text.contains("Markdown Description:"));
        assertTrue(text.contains("This markdown description should be preferred"));
        assertTrue(text.contains("tool=web-fetch"));
    }

    @Test
    void shouldGenerateSkillSkeletonFromMarkdown(@TempDir Path tempDir) throws Exception {
        Path markdown = tempDir.resolve("OpenClaw Skill.md");
        Files.writeString(
                markdown,
                """
                        # Fetch Web And Summarize

                        Use web-fetch first, then summarize the fetched content.
                        """,
                StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("generated-skill");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            SkillCli.main(new String[] {
                "convert-markdown",
                "--input",
                markdown.toString(),
                "--output-dir",
                outputDir.toString(),
                "--no-ai"
            });
        } finally {
            System.setOut(original);
        }

        String yaml = Files.readString(outputDir.resolve("skill.yaml"), StandardCharsets.UTF_8);
        String copiedMarkdown = Files.readString(outputDir.resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("name: 'generated-skill'"));
        assertTrue(yaml.contains("toolName: llm"));
        assertTrue(yaml.contains("Keep SKILL.md as the human description."));
        assertTrue(copiedMarkdown.contains("Fetch Web And Summarize"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Mode: local-skeleton"));
    }

    @Test
    void shouldGenerateSkillDraftWithAiAssistance(@TempDir Path tempDir) throws Exception {
        Path markdown = tempDir.resolve("demo-skill.md");
        Files.writeString(
                markdown,
                """
                        # Fetch Web And Summarize

                        Use web-fetch first and then summarize the fetched page.
                        """,
                StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("generated-skill");
        AppConfig config = AppConfig.load();
        ToolExecutor fakeToolExecutor = new ToolExecutor(new ToolCatalogService(config), config) {
            @Override
            public ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("id", "fetch");
                step.put("type", "tool");
                step.put("toolName", "web-fetch");
                step.put("outputKey", "webContent");
                step.put("arguments", Map.of("url", "${url}"));

                Map<String, Object> summarize = new LinkedHashMap<>();
                summarize.put("id", "summarize");
                summarize.put("type", "tool");
                summarize.put("toolName", "llm");
                summarize.put("outputKey", "summary");
                summarize.put("arguments", Map.of("prompt", "Summarize ${webContent}"));

                Map<String, Object> structured = new LinkedHashMap<>();
                structured.put("name", "should-be-overridden");
                structured.put("description", "AI generated draft.");
                structured.put("arguments", List.of(Map.of(
                        "name", "url",
                        "type", "string",
                        "description", "Target URL",
                        "required", true)));
                structured.put("steps", List.of(step, summarize));
                return ToolExecutionResult.success(
                        "llm",
                        "name: should-be-overridden\ndescription: AI generated draft.\nsteps: []\n",
                        Map.of("structured", structured));
            }
        };

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            SkillCli.convertMarkdown(
                    new String[] {
                        "convert-markdown",
                        "--input",
                        markdown.toString(),
                        "--output-dir",
                        outputDir.toString(),
                        "--provider",
                        "groq",
                        "--model",
                        "llama-3.1-8b-instant"
                    },
                    fakeToolExecutor,
                    config);
        } finally {
            System.setOut(original);
        }

        String yaml = Files.readString(outputDir.resolve("skill.yaml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("name: \"generated-skill\"") || yaml.contains("name: 'generated-skill'") || yaml.contains("name: generated-skill"));
        assertTrue(yaml.contains("description: \"AI generated draft.\"") || yaml.contains("description: 'AI generated draft.'") || yaml.contains("description: AI generated draft."));
        assertTrue(yaml.contains("toolName: \"web-fetch\"") || yaml.contains("toolName: web-fetch"));
        assertTrue(yaml.contains("toolName: \"llm\"") || yaml.contains("toolName: llm"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Mode: ai-assisted"));
    }

    @Test
    void shouldFallbackToSkeletonWhenAiDraftIsStructurallyInvalid(@TempDir Path tempDir) throws Exception {
        Path markdown = tempDir.resolve("demo-skill.md");
        Files.writeString(
                markdown,
                """
                        # Invalid AI Draft Example

                        The AI output misses required step details and should fall back.
                        """,
                StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("generated-skill");
        AppConfig config = AppConfig.load();
        ToolExecutor fakeToolExecutor = new ToolExecutor(new ToolCatalogService(config), config) {
            @Override
            public ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed) {
                Map<String, Object> brokenStep = new LinkedHashMap<>();
                brokenStep.put("id", "broken");
                brokenStep.put("type", "tool");
                brokenStep.put("toolName", "llm");
                brokenStep.put("outputKey", "summary");
                brokenStep.put("arguments", Map.of());

                Map<String, Object> structured = new LinkedHashMap<>();
                structured.put("name", "broken");
                structured.put("description", "Broken AI output.");
                structured.put("arguments", List.of());
                structured.put("steps", List.of(brokenStep));
                return ToolExecutionResult.success(
                        "llm",
                        "name: broken\ndescription: Broken AI output.\n",
                        Map.of("structured", structured));
            }
        };

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            SkillCli.convertMarkdown(
                    new String[] {
                        "convert-markdown",
                        "--input",
                        markdown.toString(),
                        "--output-dir",
                        outputDir.toString()
                    },
                    fakeToolExecutor,
                    config);
        } finally {
            System.setOut(original);
        }

        String yaml = Files.readString(outputDir.resolve("skill.yaml"), StandardCharsets.UTF_8);
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(yaml.contains("toolName: llm"));
        assertTrue(yaml.contains("TODO: convert the human-oriented skill description"));
        assertTrue(text.contains("Mode: local-skeleton"));
        assertTrue(text.contains("AI conversion was unavailable or invalid"));
    }
}
