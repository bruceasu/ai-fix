package me.asu.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import me.asu.ai.config.AppConfig;

public class ToolCatalogService {

    private static final List<String> BUILTIN_TOOL_NAMES = List.of(
            "echo-tool",
            "llm",
            "read-file",
            "list-files",
            "git-diff-reader",
            "project-summary-reader",
            "index-symbol-reader",
            "web-search",
            "web-fetch",
            "command-analyze",
            "command-index",
            "command-understand",
            "command-fix-suggest");
    private static final List<String> SUPPORTED_FILENAMES = List.of("tool.json", "tool.yaml", "tool.yml");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final AppConfig config;

    public ToolCatalogService() {
        this(AppConfig.load());
    }

    public ToolCatalogService(AppConfig config) {
        this.config = config;
    }

    public List<ToolDefinition> all() {
        Map<String, ToolDefinition> merged = new LinkedHashMap<>();
        loadBuiltinTools().forEach(tool -> merged.put(tool.name, tool));
        loadFilesystemTools().forEach(tool -> {
            if (merged.containsKey(tool.name)) {
                return;
            }
            merged.put(tool.name, tool);
        });
        return List.copyOf(merged.values());
    }

    public ToolDefinition getRequired(String name) {
        return all().stream()
                .filter(tool -> tool.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
    }

    private List<ToolDefinition> loadBuiltinTools() {
        return BUILTIN_TOOL_NAMES.stream()
                .map(this::loadClasspathTool)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<ToolDefinition> loadFilesystemTools() {
        Path toolsDir = config.getToolsDirectory();
        if (!Files.isDirectory(toolsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(toolsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .filter(path -> SUPPORTED_FILENAMES.contains(path.getFileName().toString()))
                    .map(this::loadFilesystemTool)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan tools directory: " + toolsDir, e);
        }
    }

    private ToolDefinition loadClasspathTool(String name) {
        for (String fileName : SUPPORTED_FILENAMES) {
            String resource = "tools/" + name + "/" + fileName;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    continue;
                }
                ToolDefinition definition = readDefinition(input, resource);
                definition.toolHome = "classpath:" + resource;
                return definition;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load tool definition: " + resource, e);
            }
        }
        return null;
    }

    private ToolDefinition loadFilesystemTool(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            ToolDefinition definition = readDefinition(input, path.getFileName().toString());
            definition.toolHome = path.toAbsolutePath().normalize().toString();
            return definition;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load tool definition: " + path, e);
        }
    }

    private ToolDefinition readDefinition(InputStream input, String sourceName) throws IOException {
        return selectMapper(sourceName).readValue(input, ToolDefinition.class);
    }

    private ObjectMapper selectMapper(String sourceName) {
        return sourceName.endsWith(".yaml") || sourceName.endsWith(".yml") ? yamlMapper : jsonMapper;
    }
}
