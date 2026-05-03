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

public class GoProjectAnalyzer implements ProjectAnalyzer {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([A-Za-z0-9_]+)", Pattern.MULTILINE);
    private static final Pattern FUNC_PATTERN = Pattern.compile("^\\s*func\\s+(?:\\([^)]*\\)\\s*)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern STRUCT_PATTERN = Pattern.compile("^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+struct\\b", Pattern.MULTILINE);
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+interface\\b", Pattern.MULTILINE);

    @Override
    public ProjectSummary analyze(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        ProjectSummary summary = new ProjectSummary();
        summary.projectRoot = ".";
        summary.projectName = normalizedRoot.getFileName() == null ? normalizedRoot.toString() : normalizedRoot.getFileName().toString();
        summary.primaryLanguage = "go";

        detectBuildFiles(normalizedRoot, summary);

        Set<String> packages = new TreeSet<>();
        Set<String> entryPoints = new LinkedHashSet<>();
        Set<String> integrations = new TreeSet<>();
        List<ProjectSummary.ComponentSummary> components = new ArrayList<>();

        try (var paths = Files.walk(normalizedRoot)) {
            List<Path> goFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".go"))
                    .collect(Collectors.toList());
            summary.sourceFileCount = goFiles.size();
            summary.goFileCount = goFiles.size();

            for (Path file : goFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String packageName = matchFirst(PACKAGE_PATTERN, content, "(default)");
                packages.add(packageName);
                collectIntegrations(content, integrations);

                int funcCount = countMatches(FUNC_PATTERN, content);
                summary.functionCount += funcCount;
                summary.methodCount += funcCount;

                if ("main".equals(packageName) && content.contains("func main(")) {
                    entryPoints.add(relativize(normalizedRoot, file) + "#main");
                }

                for (String structName : matchAll(STRUCT_PATTERN, content)) {
                    ProjectSummary.ComponentSummary component = component(normalizedRoot, file, packageName, structName, "struct", funcCount);
                    components.add(component);
                    summary.classCount++;
                }
                for (String interfaceName : matchAll(INTERFACE_PATTERN, content)) {
                    ProjectSummary.ComponentSummary component = component(normalizedRoot, file, packageName, interfaceName, "interface", 0);
                    components.add(component);
                    summary.interfaceCount++;
                }
            }
        }

        summary.packageCount = packages.size();
        summary.packages = packages.stream().limit(20).collect(Collectors.toList());
        summary.entryPoints = new ArrayList<>(entryPoints);
        summary.externalIntegrations = integrations.stream().limit(20).collect(Collectors.toList());
        summary.topClasses = components.stream()
                .sorted(Comparator.comparingInt((ProjectSummary.ComponentSummary c) -> c.methodCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
        return summary;
    }

    private void detectBuildFiles(Path root, ProjectSummary summary) {
        addBuildFile(root, summary, "go.mod");
        addBuildFile(root, summary, "go.sum");
    }

    private void addBuildFile(Path root, ProjectSummary summary, String fileName) {
        Path file = root.resolve(fileName);
        if (Files.isRegularFile(file)) {
            summary.buildFiles.add(fileName);
        }
    }

    private void collectIntegrations(String content, Set<String> integrations) {
        if (content.contains("net/http")) integrations.add("HTTP");
        if (content.contains("database/sql")) integrations.add("Database");
        if (content.contains("grpc")) integrations.add("gRPC");
        if (content.contains("github.com/redis")) integrations.add("Redis");
    }

    private ProjectSummary.ComponentSummary component(
            Path root,
            Path file,
            String packageName,
            String name,
            String kind,
            int methodCount) {
        ProjectSummary.ComponentSummary component = new ProjectSummary.ComponentSummary();
        component.file = relativize(root, file);
        component.packageName = packageName;
        component.className = name;
        component.kind = kind;
        component.methodCount = methodCount;
        return component;
    }

    private String relativize(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString();
    }

    private String matchFirst(Pattern pattern, String content, String fallback) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : fallback;
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
