package me.asu.ai.tool;

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

class ToolCatalogServiceTest {

    @Test
    void shouldLoadBuiltinToolsFromClasspath() {
        ToolCatalogService service = new ToolCatalogService();
        List<ToolDefinition> tools = service.all();

        assertFalse(tools.isEmpty());
        assertEquals("echo-tool", tools.get(0).name);
        assertTrue(tools.stream().anyMatch(tool -> "llm".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "read-file".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "list-files".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "git-diff-reader".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "project-summary-reader".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "index-symbol-reader".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "web-search".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "web-fetch".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "command-analyze".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "command-index".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "command-understand".equals(tool.name)));
        assertTrue(tools.stream().anyMatch(tool -> "command-fix-suggest".equals(tool.name)));
    }

    @Test
    void shouldLoadFilesystemYamlToolsWithoutOverridingBuiltin(@TempDir Path tempDir) throws IOException {
        Path toolsDir = tempDir.resolve("tools").resolve("echo-tool");
        Files.createDirectories(toolsDir);
        Files.writeString(
                toolsDir.resolve("tool.yaml"),
                """
                        name: echo-tool
                        description: Echo override from yaml.
                        autoExecuteAllowed: true
                        tool:
                          type: builtin
                          command: echo_tool
                        arguments: []
                        """,
                StandardCharsets.UTF_8);

        AppConfig config = loadConfig(tempDir);
        ToolCatalogService service = new ToolCatalogService(config);
        List<ToolDefinition> tools = service.all();

        assertFalse(tools.isEmpty());
        assertEquals("echo-tool", tools.get(0).name);
        assertNotEquals("Echo override from yaml.", tools.get(0).description);
        assertTrue(tools.get(0).toolHome.startsWith("classpath:"));
    }

    @Test
    void shouldLoadFilesystemYamlToolsWithDifferentName(@TempDir Path tempDir) throws IOException {
        Path toolsDir = tempDir.resolve("tools").resolve("echo-tool-extended");
        Files.createDirectories(toolsDir);
        Files.writeString(
                toolsDir.resolve("tool.yaml"),
                """
                        name: echo-tool-extended
                        description: External echo tool from yaml.
                        autoExecuteAllowed: true
                        tool:
                          type: builtin
                          command: echo_tool
                        arguments: []
                        """,
                StandardCharsets.UTF_8);

        AppConfig config = loadConfig(tempDir);
        ToolCatalogService service = new ToolCatalogService(config);
        List<ToolDefinition> tools = service.all();

        assertTrue(tools.size() >= 5);
        ToolDefinition external = tools.stream()
                .filter(tool -> "echo-tool-extended".equals(tool.name))
                .findFirst()
                .orElseThrow();
        assertEquals("External echo tool from yaml.", external.description);
        assertTrue(external.toolHome.endsWith("tool.yaml"));
    }

    @Test
    void shouldLoadWorkspaceDatabaseTools() throws IOException {
        Path configFile = Files.createTempFile("ai-fix-tools", ".properties");
        try {
            Files.writeString(
                    configFile,
                    "tools.dir=" + toPropertiesPath(Path.of("workspace", "tools")),
                    StandardCharsets.UTF_8);
            AppConfig config = AppConfig.load(configFile.toString());
            ToolCatalogService service = new ToolCatalogService(config);
            List<ToolDefinition> tools = service.all();

            assertTrue(tools.stream().anyMatch(tool -> "mysql-client".equals(tool.name)));
            assertTrue(tools.stream().anyMatch(tool -> "pgsql-client".equals(tool.name)));
            assertTrue(tools.stream().anyMatch(tool -> "import-tool".equals(tool.name)));
            assertTrue(tools.stream().anyMatch(tool -> "import-control-file-generator".equals(tool.name)));
            assertTrue(tools.stream().anyMatch(tool -> "youtube-transcript".equals(tool.name)));
            assertTrue(tools.stream().anyMatch(tool -> "playwright-cli".equals(tool.name)));
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    private AppConfig loadConfig(Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "tools.dir=" + toPropertiesPath(tempDir.resolve("tools")),
                StandardCharsets.UTF_8);
        return AppConfig.load(configFile.toString());
    }

    private String toPropertiesPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}
