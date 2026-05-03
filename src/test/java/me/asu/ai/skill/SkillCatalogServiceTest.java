package me.asu.ai.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.asu.ai.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillCatalogServiceTest {

    @Test
    void shouldLoadBuiltinSkillsFromClasspath() {
        SkillCatalogService service = new SkillCatalogService();
        List<SkillDefinition> skills = service.all();

        assertFalse(skills.isEmpty());
        assertEquals("summarize-and-echo", skills.get(0).name);
        assertTrue(skills.stream().anyMatch(skill -> "analyze-java-error".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "search-doc-and-explain".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "analyze-test-failure".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "project-summary-and-explain".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "review-changed-files".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "explain-file".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "explain-symbol".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "find-entry-points".equals(skill.name)));
        assertTrue(skills.stream().anyMatch(skill -> "explain-project-structure".equals(skill.name)));
    }

    @Test
    void shouldLoadFilesystemYamlSkillsWithoutOverridingBuiltin(@TempDir Path tempDir) throws IOException {
        Path skillsDir = tempDir.resolve("skills").resolve("summarize-and-echo");
        Files.createDirectories(skillsDir);
        Files.writeString(
                skillsDir.resolve("skill.yml"),
                """
                        name: summarize-and-echo
                        description: Overridden yaml skill.
                        autoExecuteAllowed: true
                        arguments: []
                        steps: []
                        """,
                StandardCharsets.UTF_8);

        AppConfig config = loadConfig(tempDir);
        SkillCatalogService service = new SkillCatalogService(config);
        List<SkillDefinition> skills = service.all();

        assertFalse(skills.isEmpty());
        assertEquals("summarize-and-echo", skills.get(0).name);
        assertNotEquals("Overridden yaml skill.", skills.get(0).description);
        assertTrue(skills.get(0).skillHome.startsWith("classpath:"));
    }

    @Test
    void shouldLoadFilesystemYamlSkillsWithDifferentName(@TempDir Path tempDir) throws IOException {
        Path skillsDir = tempDir.resolve("skills").resolve("summarize-and-echo-extended");
        Files.createDirectories(skillsDir);
        Files.writeString(
                skillsDir.resolve("skill.yml"),
                """
                        name: summarize-and-echo-extended
                        description: External yaml skill.
                        autoExecuteAllowed: true
                        arguments: []
                        steps: []
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                skillsDir.resolve("SKILL.md"),
                """
                        # Summarize And Echo Extended

                        Human oriented markdown description for the external skill.
                        """,
                StandardCharsets.UTF_8);

        AppConfig config = loadConfig(tempDir);
        SkillCatalogService service = new SkillCatalogService(config);
        List<SkillDefinition> skills = service.all();

        assertTrue(skills.size() >= 4);
        SkillDefinition external = skills.stream()
                .filter(skill -> "summarize-and-echo-extended".equals(skill.name))
                .findFirst()
                .orElseThrow();
        assertEquals("External yaml skill.", external.description);
        assertTrue(external.skillHome.endsWith("skill.yml"));
        assertTrue(external.markdownHome.endsWith("SKILL.md"));
        assertTrue(external.markdownDescription.contains("Human oriented markdown description"));
    }

    @Test
    void shouldLoadWorkspaceGenerateImportSkill() throws IOException {
        Path configFile = Files.createTempFile("ai-fix-skills", ".properties");
        try {
            Files.writeString(
                    configFile,
                    "skills.dir=" + toPropertiesPath(Path.of("workspace", "skills")),
                    StandardCharsets.UTF_8);
            AppConfig config = AppConfig.load(configFile.toString());
            SkillCatalogService service = new SkillCatalogService(config);
            List<SkillDefinition> skills = service.all();

            assertTrue(skills.stream().anyMatch(skill -> "generate-import-control-and-run".equals(skill.name)));
            assertTrue(skills.stream().anyMatch(skill -> "youtube-transcript-and-summarize".equals(skill.name)));
            assertTrue(skills.stream().anyMatch(skill -> "playwright-ui-smoke-test".equals(skill.name)));
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    @Test
    void shouldFindMarkdownOnlySkillDocument(@TempDir Path tempDir) throws IOException {
        Path skillsDir = tempDir.resolve("skills").resolve("markdown-only-skill");
        Files.createDirectories(skillsDir);
        Files.writeString(
                skillsDir.resolve("SKILL.md"),
                """
                        # Markdown Only Skill

                        This skill only has markdown.
                        """,
                StandardCharsets.UTF_8);

        AppConfig config = loadConfig(tempDir);
        SkillCatalogService service = new SkillCatalogService(config);

        assertTrue(service.findMarkdownOnlySkillDocument("markdown-only-skill").isPresent());
        assertTrue(service.findMarkdownOnlySkillDocument("missing-skill").isEmpty());
    }

    private AppConfig loadConfig(Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "skills.dir=" + toPropertiesPath(tempDir.resolve("skills")),
                StandardCharsets.UTF_8);
        return AppConfig.load(configFile.toString());
    }

    private String toPropertiesPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}
