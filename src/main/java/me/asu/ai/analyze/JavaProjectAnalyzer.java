package me.asu.ai.analyze;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import me.asu.ai.model.ProjectSummary;

public class JavaProjectAnalyzer {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    public ProjectSummary analyze(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        ProjectSummary summary = new ProjectSummary();
        summary.projectRoot = ".";
        summary.projectName = normalizedRoot.getFileName() == null
                ? normalizedRoot.toString()
                : normalizedRoot.getFileName().toString();

        detectBuildFiles(normalizedRoot, summary);

        Set<String> packageNames = new TreeSet<>();
        Set<String> entryPoints = new LinkedHashSet<>();
        Set<String> integrations = new TreeSet<>();
        List<ProjectSummary.ComponentSummary> allClasses = new ArrayList<>();

        try (var paths = Files.walk(normalizedRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            summary.javaFileCount = javaFiles.size();

            for (Path file : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    String packageName = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString())
                            .orElse("(default)");
                    packageNames.add(packageName);

                    collectIntegrations(cu, integrations);

                    for (ClassOrInterfaceDeclaration declaration : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        ProjectSummary.ComponentSummary component = toComponent(normalizedRoot, file, packageName, declaration);
                        allClasses.add(component);

                        if (declaration.isInterface()) {
                            summary.interfaceCount++;
                        } else {
                            summary.classCount++;
                        }

                        int methodCount = declaration.getMethods().size();
                        summary.methodCount += methodCount;

                        if (hasAnnotation(declaration, "SpringBootApplication")) {
                            entryPoints.add(component.className);
                        }
                        if (declaration.getMethods().stream().anyMatch(this::isMainMethod)) {
                            entryPoints.add(component.className + "#main");
                        }

                        categorizeComponent(summary, component);
                    }

                    summary.enumCount += cu.findAll(EnumDeclaration.class).size();
                } catch (Exception e) {
                    System.out.println("Warning: parse failed for " + file + " - " + e.getMessage());
                }
            }
        }

        summary.packageCount = packageNames.size();
        summary.packages = packageNames.stream().limit(20).collect(Collectors.toList());
        summary.entryPoints = new ArrayList<>(entryPoints);
        summary.externalIntegrations = integrations.stream().limit(20).collect(Collectors.toList());
        summary.topClasses = allClasses.stream()
                .sorted(Comparator.comparingInt((ProjectSummary.ComponentSummary c) -> c.methodCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
        trimCategory(summary.controllers);
        trimCategory(summary.services);
        trimCategory(summary.repositories);
        trimCategory(summary.configs);
        return summary;
    }

    private void detectBuildFiles(Path root, ProjectSummary summary) {
        addBuildFile(root, summary, "pom.xml");
        addBuildFile(root, summary, "build.gradle");
        addBuildFile(root, summary, "build.gradle.kts");
        addBuildFile(root, summary, "settings.gradle");
        addBuildFile(root, summary, "settings.gradle.kts");
        summary.mavenProject = summary.buildFiles.stream().anyMatch(name -> name.equals("pom.xml"));
        summary.gradleProject = summary.buildFiles.stream().anyMatch(name -> name.startsWith("build.gradle"));
    }

    private void addBuildFile(Path root, ProjectSummary summary, String fileName) {
        Path file = root.resolve(fileName);
        if (Files.isRegularFile(file)) {
            summary.buildFiles.add(fileName);
        }
    }

    private void collectIntegrations(CompilationUnit cu, Set<String> integrations) {
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.contains("feign")) {
                integrations.add("Feign");
            }
            if (name.contains("kafka")) {
                integrations.add("Kafka");
            }
            if (name.contains("redis")) {
                integrations.add("Redis");
            }
            if (name.contains("jpa") || name.contains("mybatis")) {
                integrations.add("Database");
            }
            if (name.contains("amqp") || name.contains("rabbit")) {
                integrations.add("RabbitMQ");
            }
            if (name.contains("web.client") || name.contains("resttemplate") || name.contains("okhttp")) {
                integrations.add("HTTP Client");
            }
        }
    }

    private ProjectSummary.ComponentSummary toComponent(
            Path root,
            Path file,
            String packageName,
            ClassOrInterfaceDeclaration declaration) {
        ProjectSummary.ComponentSummary component = new ProjectSummary.ComponentSummary();
        component.file = root.relativize(file.toAbsolutePath().normalize()).toString();
        component.packageName = packageName;
        component.className = declaration.getNameAsString();
        component.kind = declaration.isInterface() ? "interface" : "class";
        component.methodCount = declaration.getMethods().size();
        declaration.getAnnotations().forEach(annotation -> component.annotations.add(annotation.getNameAsString()));
        return component;
    }

    private void categorizeComponent(ProjectSummary summary, ProjectSummary.ComponentSummary component) {
        if (component.annotations.contains("RestController") || component.annotations.contains("Controller")) {
            summary.controllerCount++;
            summary.controllers.add(component);
        }
        if (component.annotations.contains("Service")) {
            summary.serviceCount++;
            summary.services.add(component);
        }
        if (component.annotations.contains("Repository")) {
            summary.repositoryCount++;
            summary.repositories.add(component);
        }
        if (component.annotations.contains("Configuration")) {
            summary.configCount++;
            summary.configs.add(component);
        }
        if (component.annotations.contains("Component")) {
            summary.componentCount++;
        }
    }

    private boolean hasAnnotation(ClassOrInterfaceDeclaration declaration, String annotation) {
        return declaration.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals(annotation));
    }

    private boolean isMainMethod(MethodDeclaration method) {
        return method.isStatic()
                && method.isPublic()
                && method.getNameAsString().equals("main");
    }

    private void trimCategory(List<ProjectSummary.ComponentSummary> components) {
        components.sort(Comparator.comparingInt((ProjectSummary.ComponentSummary c) -> c.methodCount).reversed());
        if (components.size() > 10) {
            components.subList(10, components.size()).clear();
        }
    }
}
