package me.asu.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ExternalCommandSupport {

    public ToolExecutionResult execute(ToolDefinition definition, JsonNode args) {
        try {
            if (definition.tool == null || definition.tool.program == null || definition.tool.program.isBlank()) {
                return ToolExecutionResult.failure(definition.name, "Missing external command program");
            }
            Path toolDir = resolveToolDirectory(definition);
            List<String> command = new ArrayList<>();
            command.addAll(resolveProgramCommand(definition.tool.program, toolDir));
            for (String token : definition.tool.args) {
                command.add(renderToken(token, args, toolDir));
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            Path workingDir = definition.tool.workingDirectory == null || definition.tool.workingDirectory.isBlank()
                    ? toolDir
                    : resolvePathToken(definition.tool.workingDirectory, toolDir, toolDir);
            builder.directory(workingDir.toFile());
            Process process = builder.start();

            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = stderr.isBlank() ? "external command failed with exit code " + exitCode : stderr.trim();
                return ToolExecutionResult.failure(definition.name, error);
            }
            return ToolExecutionResult.success(definition.name, stdout.trim(), java.util.Map.of(
                    "type", "external-command",
                    "program", command.getFirst(),
                    "workingDirectory", workingDir.toString().replace('\\', '/'),
                    "exitCode", exitCode));
        } catch (Exception e) {
            return ToolExecutionResult.failure(definition.name, e.getMessage());
        }
    }

    private Path resolveToolDirectory(ToolDefinition definition) {
        Path home = Path.of(definition.toolHome).toAbsolutePath().normalize();
        return Files.isDirectory(home) ? home : home.getParent();
    }

    private List<String> resolveProgramCommand(String program, Path toolDir) {
        if ("python".equalsIgnoreCase(program)) {
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
        return List.of(resolvePathToken(program, toolDir, toolDir).toString());
    }

    private String renderToken(String token, JsonNode args, Path toolDir) {
        String rendered = token.replace("${toolHome}", toolDir.toString().replace('\\', '/'));
        if (args != null && args.isObject()) {
            var names = args.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                rendered = rendered.replace("${" + name + "}", args.path(name).asText(""));
            }
        }
        return resolvePathToken(rendered, toolDir, toolDir).toString();
    }

    private Path resolvePathToken(String token, Path toolDir, Path fallbackDir) {
        if (token == null || token.isBlank()) {
            return fallbackDir;
        }
        String normalized = token.replace('/', java.io.File.separatorChar);
        Path path = Path.of(normalized);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (token.startsWith(".") || token.contains("/") || token.contains("\\")) {
            return toolDir.resolve(path).normalize();
        }
        return path;
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

    private String readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
