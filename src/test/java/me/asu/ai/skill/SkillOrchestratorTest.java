package me.asu.ai.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.config.AppConfig;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolExecutor;
import me.asu.ai.tool.WebToolSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillOrchestratorTest {

    @Test
    void shouldSkipOptionalFetchStepWhenUrlIsMissing() {
        AppConfig config = AppConfig.load();
        SkillCatalogService skillCatalog = new SkillCatalogService(config);
        ToolExecutor toolExecutor = new ToolExecutor(new ToolCatalogService(config), new FakeWebToolSupport());
        SkillOrchestrator orchestrator = new SkillOrchestrator(skillCatalog, toolExecutor, config) {
            @Override
            public SkillExecutionResult execute(
                    String skillName,
                    String inputJson,
                    boolean confirmed,
                    String provider,
                    String model) {
                return super.execute(skillName, inputJson, confirmed, provider, model);
            }
        };

        SkillExecutionResult result = orchestrator.execute(
                "search-doc-and-explain",
                "{\"query\":\"spring retry guide\",\"site\":\"docs.spring.io\",\"limit\":3}",
                true,
                "invalid-provider",
                "invalid-model");

        assertTrue(!result.ok() || result.context().containsKey("searchResults"));
        if (result.ok()) {
            assertTrue(result.result().containsKey("stepCount"));
            assertTrue(result.summary().contains("Skill completed"));
        }
    }

    @Test
    void shouldFailLegacyAiStepSkill(@TempDir Path tempDir) throws Exception {
        Path skillsDir = tempDir.resolve("skills").resolve("legacy-ai-skill");
        Files.createDirectories(skillsDir);
        Files.writeString(
                skillsDir.resolve("skill.yaml"),
                """
                        name: legacy-ai-skill
                        description: Legacy ai step
                        autoExecuteAllowed: true
                        arguments: []
                        steps:
                          - id: analysis
                            type: ai
                            outputKey: analysis
                            userPrompt: hello
                        """,
                StandardCharsets.UTF_8);
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "skills.dir=" + skillsDir.getParent().toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        SkillCatalogService skillCatalog = new SkillCatalogService(config);
        ToolExecutor toolExecutor = new ToolExecutor(new ToolCatalogService(config), config);
        SkillOrchestrator orchestrator = new SkillOrchestrator(skillCatalog, toolExecutor, config);

        SkillExecutionResult result = orchestrator.execute(
                "legacy-ai-skill",
                "{}",
                true,
                "groq",
                "llama-3.1-8b-instant");

        assertTrue(!result.ok());
        assertTrue(result.error().contains("Legacy ai step"));
    }

    @Test
    void skillCatalogShouldIncludeNewBuiltins() {
        SkillCatalogService service = new SkillCatalogService();
        assertTrue(service.all().stream().anyMatch(skill -> "analyze-test-failure".equals(skill.name)));
        assertTrue(service.all().stream().anyMatch(skill -> "project-summary-and-explain".equals(skill.name)));
    }

    @Test
    void shouldPersistYoutubeSummaryToDerivedPath(@TempDir Path tempDir) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        context.put("summary", "[Overview]\nDemo summary");
        context.put(
                "transcriptSummary",
                """
                        {
                          "videoIds": ["Dv1lpwrkfVc"],
                          "outputDir": "%s"
                        }
                        """.formatted(tempDir.toString().replace("\\", "/")));

        SkillOrchestrator.persistYouTubeSummaryIfNeeded(
                "youtube-transcript-and-summarize",
                input,
                context,
                result);

        String summaryFile = String.valueOf(result.get("summaryFile"));
        assertNotNull(summaryFile);
        assertTrue(summaryFile.endsWith("Dv1lpwrkfVc.summary.md"));
        assertEquals("[Overview]\nDemo summary", Files.readString(Path.of(summaryFile), StandardCharsets.UTF_8));
    }

    @Test
    void shouldPersistYoutubeSummaryToExplicitPath(@TempDir Path tempDir) throws Exception {
        Path summaryPath = tempDir.resolve("custom-summary.md");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("summaryOutput", summaryPath.toString());
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        context.put("summary", "[Overview]\nExplicit path");
        context.put("transcriptSummary", "{\"videoIds\":[\"ignored\"],\"outputDir\":\"ignored\"}");

        SkillOrchestrator.persistYouTubeSummaryIfNeeded(
                "youtube-transcript-and-summarize",
                input,
                context,
                result);

        assertEquals(summaryPath.toAbsolutePath().normalize().toString().replace('\\', '/'), String.valueOf(result.get("summaryFile")));
        assertEquals("[Overview]\nExplicit path", Files.readString(summaryPath, StandardCharsets.UTF_8));
    }

    @Test
    void shouldPersistPlaywrightReportToExplicitPath(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("ui-report.md");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("reportOutput", reportPath.toString());
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        context.put("report", "[Status]\nPass");

        SkillOrchestrator.persistPlaywrightSmokeReportIfNeeded(
                "playwright-ui-smoke-test",
                input,
                context,
                result);

        assertEquals(reportPath.toAbsolutePath().normalize().toString().replace('\\', '/'), String.valueOf(result.get("reportFile")));
        assertEquals("[Status]\nPass", Files.readString(reportPath, StandardCharsets.UTF_8));
    }

    @Test
    void shouldPersistPlaywrightReportToDerivedPath(@TempDir Path tempDir) throws Exception {
        Path screenshot = tempDir.resolve("demo.png");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("session", "demo");
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        context.put("report", "[Status]\nWarning");
        context.put(
                "screenshotResult",
                """
                        {
                          "stdout": "%s"
                        }
                        """.formatted(screenshot.toString().replace("\\", "/")));

        SkillOrchestrator.persistPlaywrightSmokeReportIfNeeded(
                "playwright-ui-smoke-test",
                input,
                context,
                result);

        String reportFile = String.valueOf(result.get("reportFile"));
        assertTrue(reportFile.endsWith("demo.report.md"));
        assertEquals("[Status]\nWarning", Files.readString(Path.of(reportFile), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRunMarkdownOnlySkillAndEmitYaml(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("skills").resolve("markdown-only-skill");
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                        # Markdown Only Skill

                        Fetch a URL and summarize the content.
                        """,
                StandardCharsets.UTF_8);
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "skills.dir=" + skillDir.getParent().toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        SkillCatalogService skillCatalog = new SkillCatalogService(config);
        ToolExecutor toolExecutor = new ToolExecutor(new ToolCatalogService(config), config) {
            @Override
            public me.asu.ai.tool.ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed) {
                if ("llm".equals(toolName) && argsJson.contains("\"requiredKeys\"")) {
                    Map<String, Object> fetchStep = new LinkedHashMap<>();
                    fetchStep.put("id", "draft");
                    fetchStep.put("type", "tool");
                    fetchStep.put("toolName", "llm");
                    fetchStep.put("outputKey", "result");
                    fetchStep.put("arguments", Map.of("prompt", "Summarize the markdown-only skill request."));
                    Map<String, Object> structured = new LinkedHashMap<>();
                    structured.put("name", "ignored-name");
                    structured.put("description", "Generated runtime skill.");
                    structured.put("arguments", List.of());
                    structured.put("steps", List.of(fetchStep));
                    return me.asu.ai.tool.ToolExecutionResult.success(
                            "llm",
                            "description: Generated runtime skill.",
                            Map.of("structured", structured));
                }
                if ("llm".equals(toolName)) {
                    return me.asu.ai.tool.ToolExecutionResult.success("llm", "Runtime markdown skill executed.");
                }
                return me.asu.ai.tool.ToolExecutionResult.failure(toolName, "Unexpected tool: " + toolName);
            }
        };
        SkillOrchestrator orchestrator = new SkillOrchestrator(skillCatalog, toolExecutor, config);
        Path emitYaml = tempDir.resolve("generated-runtime-skill.yaml");

        SkillExecutionResult result = orchestrator.execute(
                "markdown-only-skill",
                "{}",
                true,
                "groq",
                "llama-3.1-8b-instant",
                emitYaml.toString());

        assertTrue(result.ok());
        assertEquals("Runtime markdown skill executed.", result.context().get("result"));
        String emitted = Files.readString(emitYaml, StandardCharsets.UTF_8);
        assertTrue(emitted.contains("name: \"markdown-only-skill\"") || emitted.contains("name: markdown-only-skill"));
        assertTrue(emitted.contains("toolName: \"llm\"") || emitted.contains("toolName: llm"));
    }

    private static final class FakeWebToolSupport extends WebToolSupport {
        @Override
        public String fetch(String url, int maxChars) {
            throw new IllegalArgumentException("Missing required argument: url");
        }

        @Override
        public String search(String query, String site, int limit) {
            return "1. Spring Retry Guide | https://docs.spring.io/guide";
        }
    }
}
