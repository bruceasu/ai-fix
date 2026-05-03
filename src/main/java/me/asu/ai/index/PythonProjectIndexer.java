package me.asu.ai.index;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.asu.ai.model.MethodInfo;

public class PythonProjectIndexer implements ProjectIndexer {

    private static final Pattern DEF_PATTERN = Pattern.compile("^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(", Pattern.MULTILINE);

    @Override
    public List<MethodInfo> build(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<MethodInfo> results = new ArrayList<>();

        try (var paths = Files.walk(normalizedRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".py")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String moduleName = normalizedRoot.relativize(file.toAbsolutePath().normalize())
                        .toString()
                        .replace("\\", ".")
                        .replace("/", ".")
                        .replaceAll("\\.py$", "");
                Matcher matcher = DEF_PATTERN.matcher(content);
                while (matcher.find()) {
                    MethodInfo info = new MethodInfo();
                    info.projectRoot = ".";
                    info.language = "python";
                    info.file = normalizedRoot.relativize(file.toAbsolutePath().normalize()).toString();
                    info.packageName = moduleName;
                    info.symbolType = "function";
                    info.containerName = moduleName;
                    info.className = moduleName;
                    info.methodName = matcher.group(1);
                    info.symbolName = matcher.group(1);
                    info.signature = matcher.group().trim();
                    info.beginLine = lineNumber(content, matcher.start());
                    info.endLine = findPythonBlockEnd(content, info.beginLine);
                    info.annotations = new ArrayList<>();
                    info.calls = new ArrayList<>();
                    results.add(info);
                }
            }
        }
        return results;
    }

    private int lineNumber(String content, int offset) {
        return 1 + (int) content.substring(0, offset).chars().filter(ch -> ch == '\n').count();
    }

    private int findPythonBlockEnd(String content, int beginLine) {
        String[] lines = content.split("\n", -1);
        int currentLine = beginLine - 1;
        if (currentLine < 0 || currentLine >= lines.length) {
            return beginLine;
        }
        int baseIndent = indentation(lines[currentLine]);
        int endLine = beginLine;
        for (int i = currentLine + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int indent = indentation(line);
            if (indent <= baseIndent && !line.stripLeading().startsWith("@")) {
                break;
            }
            endLine = i + 1;
        }
        return endLine;
    }

    private int indentation(String line) {
        int count = 0;
        while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
            count++;
        }
        return count;
    }
}
