package me.asu.ai.index;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.asu.ai.model.MethodInfo;

public class GoProjectIndexer implements ProjectIndexer {

    private static final Pattern FUNC_PATTERN = Pattern.compile(
            "^\\s*func\\s+(?:\\(([^)]*)\\)\\s*)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\(",
            Pattern.MULTILINE);
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([A-Za-z0-9_]+)", Pattern.MULTILINE);

    @Override
    public List<MethodInfo> build(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<MethodInfo> results = new ArrayList<>();

        try (var paths = Files.walk(normalizedRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".go")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String packageName = matchFirst(PACKAGE_PATTERN, content, "(default)");
                Matcher matcher = FUNC_PATTERN.matcher(content);
                while (matcher.find()) {
                    MethodInfo info = new MethodInfo();
                    info.projectRoot = ".";
                    info.language = "go";
                    info.file = normalizedRoot.relativize(file.toAbsolutePath().normalize()).toString();
                    info.packageName = packageName;
                    info.symbolType = matcher.group(1) == null ? "function" : "method";
                    info.containerName = packageName;
                    info.className = packageName;
                    info.methodName = matcher.group(2);
                    info.symbolName = matcher.group(2);
                    info.signature = matcher.group().trim();
                    info.beginLine = lineNumber(content, matcher.start());
                    info.endLine = findGoBlockEnd(content, matcher.end(), info.beginLine);
                    info.annotations = new ArrayList<>();
                    info.calls = new ArrayList<>();
                    results.add(info);
                }
            }
        }
        return results;
    }

    private String matchFirst(Pattern pattern, String content, String fallback) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private int lineNumber(String content, int offset) {
        return 1 + (int) content.substring(0, offset).chars().filter(ch -> ch == '\n').count();
    }

    private int findGoBlockEnd(String content, int offset, int fallbackLine) {
        int braceStart = content.indexOf('{', offset);
        if (braceStart < 0) {
            return fallbackLine;
        }
        int depth = 0;
        for (int i = braceStart; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return lineNumber(content, i);
                }
            }
        }
        return fallbackLine;
    }
}
