package me.asu.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Stream;
import me.asu.ai.config.AppConfig;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;

public class ToolExecutor {

    private final ToolCatalogService toolCatalogService;
    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebToolSupport webToolSupport;
    private final GitDiffSupport gitDiffSupport;
    private final ProjectIndexSupport projectIndexSupport;
    private final CommandBridgeSupport commandBridgeSupport;
    private final ExternalCommandSupport externalCommandSupport;
    private final JdbcMetadataSupport jdbcMetadataSupport;

    public ToolExecutor(ToolCatalogService toolCatalogService) {
        this(toolCatalogService, AppConfig.load());
    }

    public ToolExecutor(ToolCatalogService toolCatalogService, AppConfig config) {
        this(toolCatalogService, config, new WebToolSupport(), new GitDiffSupport(), new ProjectIndexSupport(), new CommandBridgeSupport(config), new ExternalCommandSupport(), new JdbcMetadataSupport());
    }

    public ToolExecutor(ToolCatalogService toolCatalogService, WebToolSupport webToolSupport) {
        this(toolCatalogService, AppConfig.load(), webToolSupport, new GitDiffSupport(), new ProjectIndexSupport(), new CommandBridgeSupport(AppConfig.load()), new ExternalCommandSupport(), new JdbcMetadataSupport());
    }

    public ToolExecutor(
            ToolCatalogService toolCatalogService,
            WebToolSupport webToolSupport,
            GitDiffSupport gitDiffSupport) {
        this(toolCatalogService, AppConfig.load(), webToolSupport, gitDiffSupport, new ProjectIndexSupport(), new CommandBridgeSupport(AppConfig.load()), new ExternalCommandSupport(), new JdbcMetadataSupport());
    }

    public ToolExecutor(
            ToolCatalogService toolCatalogService,
            WebToolSupport webToolSupport,
            GitDiffSupport gitDiffSupport,
            ProjectIndexSupport projectIndexSupport) {
        this(toolCatalogService, AppConfig.load(), webToolSupport, gitDiffSupport, projectIndexSupport, new CommandBridgeSupport(AppConfig.load()), new ExternalCommandSupport(), new JdbcMetadataSupport());
    }

    public ToolExecutor(
            ToolCatalogService toolCatalogService,
            WebToolSupport webToolSupport,
            GitDiffSupport gitDiffSupport,
            ProjectIndexSupport projectIndexSupport,
            CommandBridgeSupport commandBridgeSupport) {
        this(toolCatalogService, AppConfig.load(), webToolSupport, gitDiffSupport, projectIndexSupport, commandBridgeSupport, new ExternalCommandSupport(), new JdbcMetadataSupport());
    }

    public ToolExecutor(
            ToolCatalogService toolCatalogService,
            AppConfig config,
            WebToolSupport webToolSupport,
            GitDiffSupport gitDiffSupport,
            ProjectIndexSupport projectIndexSupport,
            CommandBridgeSupport commandBridgeSupport,
            ExternalCommandSupport externalCommandSupport,
            JdbcMetadataSupport jdbcMetadataSupport) {
        this.toolCatalogService = toolCatalogService;
        this.config = config == null ? AppConfig.load() : config;
        this.webToolSupport = webToolSupport;
        this.gitDiffSupport = gitDiffSupport;
        this.projectIndexSupport = projectIndexSupport;
        this.commandBridgeSupport = commandBridgeSupport;
        this.externalCommandSupport = externalCommandSupport;
        this.jdbcMetadataSupport = jdbcMetadataSupport;
    }

    public ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed) {
        ToolDefinition definition = toolCatalogService.getRequired(toolName);
        if (!definition.autoExecuteAllowed && !confirmed) {
            return ToolExecutionResult.failure(
                    toolName,
                    "Tool requires confirmation. Re-run with --confirm to execute.");
        }
        try {
            JsonNode args = mapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            if ("external-command".equalsIgnoreCase(definition.tool.type)) {
                return externalCommandSupport.execute(definition, args);
            }
            return switch (definition.tool.command) {
                case "echo_tool" -> executeEchoTool(toolName, args);
                case "llm_call" -> executeLlmCall(toolName, args);
                case "read_file" -> executeReadFile(toolName, args);
                case "list_files" -> executeListFiles(toolName, args);
                case "git_diff_reader" -> executeGitDiffReader(toolName);
                case "project_summary_reader" -> executeProjectSummaryReader(toolName, args);
                case "index_symbol_reader" -> executeIndexSymbolReader(toolName, args);
                case "web_fetch" -> executeWebFetch(toolName, args);
                case "web_search" -> executeWebSearch(toolName, args);
                case "db_metadata_reader" -> executeDbMetadataReader(toolName, args);
                case "command_analyze" -> commandBridgeSupport.executeAnalyze(toolName, args);
                case "command_index" -> commandBridgeSupport.executeIndex(toolName, args);
                case "command_understand" -> commandBridgeSupport.executeUnderstand(toolName, args);
                case "command_fix_suggest" -> commandBridgeSupport.executeFixSuggest(toolName, args);
                default -> ToolExecutionResult.failure(toolName, "Unsupported builtin tool command: " + definition.tool.command);
            };
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, e.getMessage());
        }
    }

    private ToolExecutionResult executeDbMetadataReader(String toolName, JsonNode args) throws Exception {
        String url = args.path("url").asText("");
        String user = args.path("user").asText("");
        String password = args.path("password").asText("");
        String table = args.path("table").asText("");

        if (url.isBlank() || user.isBlank() || table.isBlank()) {
            return ToolExecutionResult.failure(toolName, "Missing required arguments: url, user, table");
        }

        String output = jdbcMetadataSupport.getTableMetadata(url, user, password, table);
        return ToolExecutionResult.success(toolName, output, Map.of(
                "url", url,
                "user", user,
                "table", table));
    }

    private ToolExecutionResult executeEchoTool(String toolName, JsonNode args) {
        String text = args.path("text").asText("");
        String prefix = args.path("prefix").asText("");
        return ToolExecutionResult.success(toolName, prefix + text, Map.of(
                "text", text,
                "prefix", prefix));
    }

    private ToolExecutionResult executeLlmCall(String toolName, JsonNode args) {
        String prompt = args.path("prompt").asText("");
        if (prompt.isBlank()) {
            return ToolExecutionResult.failure(toolName, "Missing required argument: prompt");
        }
        String provider = args.path("provider").asText(config.get("provider", AppConfig.DEFAULT_PROVIDER));
        String model = args.path("model").asText(config.get("model", AppConfig.DEFAULT_MODEL));
        String systemPrompt = args.path("systemPrompt").asText("");
        String format = args.path("format").asText("text").trim().toLowerCase();
        String requiredKeys = args.path("requiredKeys").asText("").trim();
        String effectivePrompt = systemPrompt.isBlank()
                ? prompt
                : "System instruction:\n" + systemPrompt + "\n\nUser request:\n" + prompt;
        LLMClient llm = createLlmClient(provider, model);
        String output = llm.generateText(effectivePrompt);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", provider);
        data.put("model", model);
        data.put("promptLength", prompt.length());
        data.put("hasSystemPrompt", !systemPrompt.isBlank());
        data.put("format", format);
        if (!requiredKeys.isBlank()) {
            data.put("requiredKeys", Arrays.stream(requiredKeys.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList());
        }
        try {
            if ("json".equals(format)) {
                Map<String, Object> structured = mapper.readValue(stripFence(output), new TypeReference<Map<String, Object>>() {
                });
                validateRequiredKeys(requiredKeys, structured);
                data.put("structured", structured);
            } else if ("yaml".equals(format)) {
                com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
                        new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
                Map<String, Object> structured = yamlMapper.readValue(stripFence(output), new TypeReference<Map<String, Object>>() {
                });
                validateRequiredKeys(requiredKeys, structured);
                data.put("structured", structured);
            } else if (!"text".equals(format)) {
                return ToolExecutionResult.failure(toolName, "Unsupported format: " + format + ". Use text, yaml, or json.");
            }
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, "Failed to parse " + format + " response: " + e.getMessage());
        }
        return ToolExecutionResult.success(toolName, output, data);
    }

    protected LLMClient createLlmClient(String provider, String model) {
        return LLMFactory.create(provider, model, config);
    }

    private void validateRequiredKeys(String requiredKeys, Map<String, Object> structured) {
        if (requiredKeys == null || requiredKeys.isBlank()) {
            return;
        }
        List<String> missing = Arrays.stream(requiredKeys.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(key -> !structured.containsKey(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required keys: " + String.join(", ", missing));
        }
    }

    private String stripFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:yaml|json|markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private ToolExecutionResult executeReadFile(String toolName, JsonNode args) throws Exception {
        String pathValue = args.path("path").asText("");
        if (pathValue.isBlank()) {
            return ToolExecutionResult.failure(toolName, "Missing required argument: path");
        }
        int maxChars = args.path("maxChars").asInt(8000);
        Path path = Path.of(pathValue).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return ToolExecutionResult.failure(toolName, "file not found: " + path);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        boolean truncated = false;
        if (content.length() > maxChars) {
            content = content.substring(0, Math.max(0, maxChars)) + "\n...(truncated)";
            truncated = true;
        }
        return ToolExecutionResult.success(toolName, content, Map.of(
                "path", path.toString().replace('\\', '/'),
                "maxChars", maxChars,
                "truncated", truncated));
    }

    private ToolExecutionResult executeGitDiffReader(String toolName) throws Exception {
        String diff = gitDiffSupport.readChangedFilesReviewInput();
        return ToolExecutionResult.success(toolName, diff, Map.of(
                "source", "git diff HEAD --"));
    }

    private ToolExecutionResult executeListFiles(String toolName, JsonNode args) throws Exception {
        String rootValue = args.path("root").asText(".");
        String glob = args.path("glob").asText("");
        int limit = Math.max(1, Math.min(200, args.path("limit").asInt(50)));
        Path root = Path.of(rootValue).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return ToolExecutionResult.failure(toolName, "directory not found: " + root);
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> files = stream
                    .filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> glob.isBlank() || path.contains(glob))
                    .sorted()
                    .limit(limit)
                    .toList();
            String output = String.join("\n", files);
            return ToolExecutionResult.success(toolName, output, Map.of(
                    "root", root.toString().replace('\\', '/'),
                    "glob", glob,
                    "limit", limit,
                    "files", files));
        }
    }

    private ToolExecutionResult executeProjectSummaryReader(String toolName, JsonNode args) throws Exception {
        String pathValue = args.path("path").asText("project-summary.json");
        Path path = Path.of(pathValue).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return ToolExecutionResult.failure(toolName, "project summary file not found: " + path);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return ToolExecutionResult.success(toolName, content, Map.of(
                "path", path.toString().replace('\\', '/')));
    }

    private ToolExecutionResult executeIndexSymbolReader(String toolName, JsonNode args) throws Exception {
        String indexPath = args.path("indexPath").asText("index.json");
        String symbol = args.path("symbol").asText("");
        String container = args.path("container").asText("");
        int limit = args.path("limit").asInt(5);
        if (symbol.isBlank()) {
            return ToolExecutionResult.failure(toolName, "Missing required argument: symbol");
        }
        String output = projectIndexSupport.explainSymbolInput(indexPath, symbol, container, limit);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("indexPath", Path.of(indexPath).toAbsolutePath().normalize().toString().replace('\\', '/'));
        data.put("symbol", symbol);
        data.put("container", container);
        data.put("limit", limit);
        data.put("matchType", output.contains("[candidates]") ? "candidates" : "target");
        return ToolExecutionResult.success(toolName, output, data);
    }

    private ToolExecutionResult executeWebFetch(String toolName, JsonNode args) throws Exception {
        String url = args.path("url").asText("");
        if (url.isBlank()) {
            return ToolExecutionResult.failure(toolName, "Missing required argument: url");
        }
        int maxChars = args.path("maxChars").asInt(8000);
        return ToolExecutionResult.success(toolName, webToolSupport.fetch(url, Math.max(200, maxChars)), Map.of(
                "url", url,
                "maxChars", Math.max(200, maxChars)));
    }

    private ToolExecutionResult executeWebSearch(String toolName, JsonNode args) throws Exception {
        String query = args.path("query").asText("");
        if (query.isBlank()) {
            return ToolExecutionResult.failure(toolName, "Missing required argument: query");
        }
        String site = args.path("site").asText("");
        int limit = args.path("limit").asInt(5);
        return ToolExecutionResult.success(toolName, webToolSupport.search(query, site, Math.max(1, Math.min(10, limit))), Map.of(
                "query", query,
                "site", site,
                "limit", Math.max(1, Math.min(10, limit))));
    }
}
