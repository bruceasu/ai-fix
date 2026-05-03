package me.asu.ai.analyze;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.asu.ai.model.ProjectSummary;

public class PythonProjectAnalyzer implements ProjectAnalyzer {

    private static final Pattern CLASS_PATTERN = Pattern.compile("^\\s*class\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.MULTILINE);
    private static final Pattern DEF_PATTERN = Pattern.compile("^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*(?:from\\s+([A-Za-z0-9_\\.]+)\\s+import|import\\s+([A-Za-z0-9_\\.]+))", Pattern.MULTILINE);

    @Override
    public ProjectSummary analyze(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        ProjectSummary summary = new ProjectSummary();
        summary.projectRoot = ".";
        summary.projectName = normalizedRoot.getFileName() == null ? normalizedRoot.toString() : normalizedRoot.getFileName().toString();
        summary.primaryLanguage = "python";

        detectBuildFiles(normalizedRoot, summary);

        Set<String> packages = new TreeSet<>();
        Set<String> entryPoints = new LinkedHashSet<>();
        Set<String> integrations = new TreeSet<>();
        List<ProjectSummary.ComponentSummary> components = new ArrayList<>();

        try (var paths = Files.walk(normalizedRoot)) {
            List<Path> pyFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".py"))
                    .collect(Collectors.toList());
            summary.sourceFileCount = pyFiles.size();
            summary.pythonFileCount = pyFiles.size();

            for (Path file : pyFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String moduleName = moduleName(normalizedRoot, file);
                packages.add(parentModule(moduleName));
                collectIntegrations(content, integrations);

                int defCount = countMatches(DEF_PATTERN, content);
                summary.functionCount += defCount;
                summary.methodCount += defCount;

                if (content.contains("if __name__ == \"__main__\":") || content.contains("if __name__ == '__main__':")) {
                    entryPoints.add(moduleName);
                }

                for (String className : matchAll(CLASS_PATTERN, content)) {
                    ProjectSummary.ComponentSummary component = new ProjectSummary.ComponentSummary();
                    component.file = relativize(normalizedRoot, file);
                    component.packageName = parentModule(moduleName);
                    component.className = className;
                    component.kind = "class";
                    component.methodCount = defCount;
                    components.add(component);
                    summary.classCount++;
                }
            }
        }

        summary.packageCount = packages.size();
        summary.packages = packages.stream().filter(s -> !s.isBlank()).limit(20).collect(Collectors.toList());
        summary.entryPoints = new ArrayList<>(entryPoints);
        summary.externalIntegrations = integrations.stream().limit(20).collect(Collectors.toList());
        summary.topClasses = components.stream()
                .sorted(Comparator.comparingInt((ProjectSummary.ComponentSummary c) -> c.methodCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
        return summary;
    }

    private void detectBuildFiles(Path root, ProjectSummary summary) {
        addBuildFile(root, summary, "pyproject.toml");
        addBuildFile(root, summary, "requirements.txt");
        addBuildFile(root, summary, "setup.py");
    }

    private void addBuildFile(Path root, ProjectSummary summary, String fileName) {
        Path file = root.resolve(fileName);
        if (Files.isRegularFile(file)) {
            summary.buildFiles.add(fileName);
        }
    }

    private void collectIntegrations(String content, Set<String> integrations) {
        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (value == null) {
                continue;
            }
            if (value.startsWith("requests") || value.startsWith("httpx") || value.startsWith("aiohttp")) integrations.add("HTTP");
            if (value.startsWith("sqlalchemy") || value.startsWith("django.db") || value.startsWith("psycopg")) integrations.add("Database");
            if (value.startsWith("redis")) integrations.add("Redis");
            if (value.startsWith("fastapi") || value.startsWith("flask") || value.startsWith("django")) integrations.add("Web Framework");
        }
    }

    private String moduleName(Path root, Path file) {
        String relative = relativize(root, file).replace("\\", "/");
        if (relative.endsWith(".py")) {
            relative = relative.substring(0, relative.length() - 3);
        }
        return relative.replace('/', '.');
    }

    private String parentModule(String moduleName) {
        int lastDot = moduleName.lastIndexOf('.');
        return lastDot > 0 ? moduleName.substring(0, lastDot) : moduleName;
    }

    private String relativize(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString();
    }

    private List<String> matchAll(Pattern pattern, String content) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private int countMatches(Pattern pattern, String content) {
        int count = 0;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
