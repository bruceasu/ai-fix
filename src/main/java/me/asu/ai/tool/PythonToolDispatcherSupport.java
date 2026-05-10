package me.asu.ai.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.asu.ai.config.AppConfig;

public class PythonToolDispatcherSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfig config;

    public PythonToolDispatcherSupport() {
        this(AppConfig.load());
    }

    public PythonToolDispatcherSupport(AppConfig config) {
        this.config = config == null ? AppConfig.load() : config;
    }

    public ToolExecutionResult dispatchByLLM(String instruction, String provider, String model, boolean requireConfirm) {
        return runDispatcher("dispatch", null, instruction, requireConfirm, provider, model);
    }

    public ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed, String provider, String model) {
        return runDispatcher("execute", toolName, argsJson, confirmed, provider, model);
    }

    private ToolExecutionResult runDispatcher(String mode, String toolName, String payload, boolean confirmed, String provider, String model) {
        try {
            Path dispatcherScript = resolveDispatcherScript();
            if (!Files.isRegularFile(dispatcherScript)) {
                return ToolExecutionResult.failure(toolName, "Missing Python dispatcher script: " + dispatcherScript);
            }

            List<String> command = new ArrayList<>(resolvePythonLauncher());
            command.add(dispatcherScript.toString());
            command.add("--mode");
            command.add(mode);
            command.add("--tool");
            command.add(toolName == null ? "dispatch" : toolName);
            command.add("--tools-dir");
            command.add(config.getToolsDirectory().toString());
            command.add("--instruction");
            command.add(mode.equals("dispatch") ? (payload == null ? "" : payload) : "");
            if (provider != null && !provider.isBlank()) {
                command.add("--provider");
                command.add(provider);
            }
            if (model != null && !model.isBlank()) {
                command.add("--model");
                command.add(model);
            }
            if (confirmed) {
                command.add("--confirm");
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(dispatcherScript.getParent().toFile());
            builder.environment().put("AI_FIX_PROVIDER", safeConfig("provider", provider));
            builder.environment().put("AI_FIX_MODEL", safeConfig("model", model));
            builder.environment().put("AI_FIX_PYTHON_SCRIPTS_DIR", config.getPythonScriptsDirectory().toString());
            builder.environment().put("AI_FIX_TOOLS_DIR", config.getToolsDirectory().toString());
            builder.environment().put("AI_FIX_SKILLS_DIR", config.getSkillsDirectory().toString());
            putIfPresent(builder, "OPENAI_API_KEY", "openai.api.key");
            putIfPresent(builder, "OPENAI_BASE_URL", "openai.base.url");
            putIfPresent(builder, "GROQ_API_KEY", "groq.api.key");
            putIfPresent(builder, "GROQ_BASE_URL", "groq.base.url");
            putIfPresent(builder, "OLLAMA_BASE_URL", "ollama.base.url");

            Process process = builder.start();
            if (!"dispatch".equals(mode)) {
                try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(payload == null || payload.isBlank() ? "{}" : payload);
                    writer.flush();
                }
            }
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();

            ToolExecutionResult parsed = tryParseEnvelope(stdout);
            if (parsed != null) {
                return parsed;
            }

            if (exitCode != 0) {
                String error = stderr.isBlank()
                        ? "Python dispatcher failed with exit code " + exitCode
                        : stderr.trim();
                return ToolExecutionResult.failure(toolName == null ? "dispatch" : toolName, error);
            }

            String output = stdout == null ? "" : stdout.trim();
            return ToolExecutionResult.success(toolName == null ? "dispatch" : toolName, output, java.util.Map.of(
                    "type", "python-dispatcher",
                    "command", command,
                    "exitCode", exitCode));
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName == null ? "dispatch" : toolName, e.getMessage());
        }
    }

    private Path resolveDispatcherScript() {
        return config.getPythonScriptsDirectory().resolve("tool_dispatcher.py").toAbsolutePath().normalize();
    }

    private ToolExecutionResult tryParseEnvelope(String stdout) {
        String text = stdout == null ? "" : stdout.trim();
        if (text.isBlank() || !(text.startsWith("{") && text.endsWith("}"))) {
            return null;
        }
        try {
            java.util.Map<String, Object> map = MAPPER.readValue(text, new TypeReference<java.util.Map<String, Object>>() {
            });
            if (!map.containsKey("ok") || !map.containsKey("toolName")) {
                return null;
            }
            boolean ok = Boolean.parseBoolean(String.valueOf(map.get("ok")));
            String toolName = String.valueOf(map.getOrDefault("toolName", ""));
            String output = String.valueOf(map.getOrDefault("output", ""));
            String error = String.valueOf(map.getOrDefault("error", ""));
            Object dataObj = map.get("data");
            java.util.Map<String, Object> data = dataObj instanceof java.util.Map<?, ?>
                    ? sanitizeMap(MAPPER.convertValue(dataObj, new TypeReference<java.util.Map<String, Object>>() {
                    }))
                    : java.util.Map.of();
            return new ToolExecutionResult(ok, toolName, output, data, error);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> resolvePythonLauncher() {
        List<List<String>> candidates = isWindows()
                ? List.of(
                        List.of("python"),
                        List.of("py", "-3"))
                : List.of(
                        List.of("python3"),
                        List.of("python"));
        for (List<String> candidate : candidates) {
            if (isExecutableAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No Python launcher was found. Tried: " + candidates);
    }

    private boolean isExecutableAvailable(List<String> command) {
        try {
            List<String> probe = new ArrayList<>(command);
            probe.add("--version");
            Process process = new ProcessBuilder(probe)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0 || exitCode == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWindows() {
        return File.separatorChar == '\\';
    }

    private void putIfPresent(ProcessBuilder builder, String envName, String configKey) {
        String value = config == null ? null : config.get(configKey);
        if (value != null) {
            builder.environment().put(envName, value);
        }
    }

    private String safeConfig(String configKey, String fallback) {
        String value = config == null ? null : config.get(configKey);
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }

    private String readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private java.util.Map<String, Object> sanitizeMap(java.util.Map<String, Object> input) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return result;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof java.util.Map<?, ?> nestedMap) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof java.util.List<?> nestedList) {
            java.util.ArrayList<Object> result = new java.util.ArrayList<>();
            for (Object item : nestedList) {
                if (item == null) {
                    continue;
                }
                result.add(sanitizeValue(item));
            }
            return result;
        }
        return value;
    }
}
