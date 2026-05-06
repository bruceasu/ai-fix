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
import java.util.Collections;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.parse.JavaCodeParser;
import me.asu.ai.util.IgnoreFilter;

/**
 * JavaParser 后端的 Java 项目分析器。
 *
 * 使用 JavaParser 3.28.1 分析 Java 项目结构、统计信息、依赖关系等。
 * 此类实现 {@link ProjectAnalyzer} 和 {@link JavaCodeParser} 接口，支持向后兼容和新的统一接口。
 *
 * @see JavaCodeParser
 * @see ProjectAnalyzer
 */
public class JavaProjectAnalyzer implements ProjectAnalyzer, JavaCodeParser {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    /**
     * 实现 ProjectAnalyzer 接口：分析项目。
     * 此方法保留以支持向后兼容。
     *
     * @param root 项目根目录
     * @return 项目分析总结
     * @throws Exception 如果分析失败
     */
    @Override
    public ProjectSummary analyze(Path root) throws Exception {
        return analyzeProject(root);
    }

    /**
     * 实现 JavaCodeParser 接口：索引单个文件（不支持）。
     * JavaProjectAnalyzer 只用于项目分析，不提供单文件索引功能。
     *
     * @param javaFile Java 源文件
     * @return 方法列表（不支持）
     * @throws UnsupportedOperationException 总是抛出异常
     */
    @Override
    public List<MethodInfo> indexFile(Path javaFile) throws Exception {
        throw new UnsupportedOperationException("Use JavaProjectIndexer for file indexing");
    }

    /**
     * 实现 JavaCodeParser 接口：索引项目（不支持）。
     * JavaProjectAnalyzer 只用于项目分析，不提供项目索引功能。
     *
     * @param projectRoot 项目根目录
     * @return 方法列表（不支持）
     * @throws UnsupportedOperationException 总是抛出异常
     */
    @Override
    public List<MethodInfo> indexProject(Path projectRoot) throws Exception {
        throw new UnsupportedOperationException("Use JavaProjectIndexer for project indexing");
    }

    /**
     * 实现 JavaCodeParser 接口：分析项目结构和统计信息。
     *
     * @param root 项目根目录
     * @return 项目分析总结
     * @throws Exception 如果分析失败
     */
    @Override
    public ProjectSummary analyzeProject(Path root) throws Exception {
       
        Path normalizedRoot = root.toAbsolutePath().normalize();
        ProjectSummary summary = new ProjectSummary();
        summary.projectRoot = ".";
        summary.primaryLanguage = "java";
        summary.projectName = normalizedRoot.getFileName() == null
                ? normalizedRoot.toString()
                : normalizedRoot.getFileName().toString();

        detectBuildFiles(normalizedRoot, summary);

        Set<String> packageNames = Collections.synchronizedSet(new TreeSet<>());
        Set<String> entryPoints = Collections.synchronizedSet(new LinkedHashSet<>());
        Set<String> integrations = Collections.synchronizedSet(new TreeSet<>());
        List<ProjectSummary.ComponentSummary> allClasses = Collections.synchronizedList(new ArrayList<>());
        
        IgnoreFilter filter = new IgnoreFilter(normalizedRoot);

        try (var paths = Files.walk(normalizedRoot)) {
            List<Path> allJavaFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            List<Path> javaFiles = new ArrayList<>();
            int testCount = 0;
            for (Path p : allJavaFiles) {
                if (filter.shouldIgnore(p) || isTestFile(p, normalizedRoot)) {
                    testCount++;
                } else {
                    javaFiles.add(p);
                }
            }
            
            summary.sourceFileCount = allJavaFiles.size();
            summary.javaFileCount = javaFiles.size();
            summary.testFileCount = testCount;

            javaFiles.parallelStream().forEach(file -> {
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
                        collectDependencies(summary, cu, component);

                        synchronized (summary) {
                            if (declaration.isInterface()) {
                                summary.interfaceCount++;
                            } else {
                                summary.classCount++;
                            }
                            int methodCount = declaration.getMethods().size();
                            summary.methodCount += methodCount;
                            categorizeComponent(summary, component, declaration);
                        }

                        if (hasAnnotation(declaration, "SpringBootApplication")) {
                            entryPoints.add(component.className);
                        }
                        if (declaration.getMethods().stream().anyMatch(this::isMainMethod)) {
                            entryPoints.add(component.className + "#main");
                        }
                    }

                    synchronized (summary) {
                        summary.enumCount += cu.findAll(EnumDeclaration.class).size();
                    }
                } catch (Exception e) {
                    System.out.println("Warning: parse failed for " + file + " - " + e.getMessage());
                }
            });
        }

        summary.packageCount = packageNames.size();
        summary.packages = packageNames.stream().limit(20).collect(Collectors.toList());
        summary.entryPoints = new ArrayList<>(entryPoints);
        summary.externalIntegrations = integrations.stream().limit(20).collect(Collectors.toList());
        summary.topClasses = allClasses.stream()
                .sorted(Comparator.comparingInt((ProjectSummary.ComponentSummary c) -> c.methodCount).reversed())
                .limit(15)
                .collect(Collectors.toList());
        
        trimCategory(summary.controllers);
        trimCategory(summary.services);
        trimCategory(summary.repositories);
        trimCategory(summary.configs);
        trimCategory(summary.cliClasses);
        trimCategory(summary.coreLogic);
        
        // Identify layers (simplified)
        if (summary.controllerCount > 0) summary.architecturalLayers.add("API/Controller Layer");
        if (summary.serviceCount > 0) summary.architecturalLayers.add("Service Layer");
        if (summary.repositoryCount > 0) summary.architecturalLayers.add("Repository/Persistence Layer");
        if (!summary.cliClasses.isEmpty()) summary.architecturalLayers.add("CLI/Entry Layer");
        if (!summary.coreLogic.isEmpty()) summary.architecturalLayers.add("Core Logic/Orchestration Layer");
        
        return summary;
    }

    @Override
    public String getBackendName() {
        return "JavaParser 3.28.1";
    }

    @Override
    public boolean isAvailable() {
        return true; // JavaParser 总是通过 Maven 依赖可用
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
            if (name.contains("feign")) integrations.add("Feign");
            if (name.contains("kafka")) integrations.add("Kafka");
            if (name.contains("redis")) integrations.add("Redis");
            if (name.contains("jpa") || name.contains("mybatis")) integrations.add("Database");
            if (name.contains("amqp") || name.contains("rabbit")) integrations.add("RabbitMQ");
            if (name.contains("web.client") || name.contains("resttemplate") || name.contains("okhttp")) integrations.add("HTTP Client");
        }
    }

    private void collectDependencies(ProjectSummary summary, CompilationUnit cu, ProjectSummary.ComponentSummary component) {
        cu.getImports().forEach(imp -> {
            String name = imp.getNameAsString();
            if (name.startsWith("me.asu.ai")) {
                String targetClass = name.substring(name.lastIndexOf('.') + 1);
                ProjectSummary.DependencyLink link = new ProjectSummary.DependencyLink();
                link.source = component.className;
                link.target = targetClass;
                link.type = "imports";
                synchronized (summary.dependencies) {
                    summary.dependencies.add(link);
                }
            }
        });
    }

    private ProjectSummary.ComponentSummary toComponent(
            Path root,
            Path file,
            String packageName,
            ClassOrInterfaceDeclaration declaration) {
        ProjectSummary.ComponentSummary component = new ProjectSummary.ComponentSummary();
        component.file = root.relativize(file.toAbsolutePath().normalize()).toString().replace("\\", "/");
        component.packageName = packageName;
        component.className = declaration.getNameAsString();
        component.kind = declaration.isInterface() ? "interface" : "class";
        component.methodCount = declaration.getMethods().size();
        declaration.getAnnotations().forEach(annotation -> component.annotations.add(annotation.getNameAsString()));
        return component;
    }

    private void categorizeComponent(ProjectSummary summary, ProjectSummary.ComponentSummary component, ClassOrInterfaceDeclaration declaration) {
        // Annotation-based
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

        // Heuristic-based
        String pkg = component.packageName.toLowerCase();
        if (pkg.contains(".cli") || declaration.getMethods().stream().anyMatch(this::isMainMethod)) {
            summary.cliClasses.add(component);
        } else if (pkg.contains(".skill") || pkg.contains(".tool") || pkg.contains(".patch") || pkg.contains(".analyze") || pkg.contains(".fix")) {
            summary.coreLogic.add(component);
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

    private boolean isTestFile(Path file, Path root) {
        String relative = root.relativize(file.toAbsolutePath().normalize()).toString().replace("\\", "/");
        return relative.contains("/test/") || relative.contains("/tests/") || file.getFileName().toString().endsWith("Test.java");
    }

    private void trimCategory(List<ProjectSummary.ComponentSummary> components) {
        components.sort(Comparator.comparingInt((ProjectSummary.ComponentSummary c) -> c.methodCount).reversed());
        if (components.size() > 10) {
            components.subList(10, components.size()).clear();
        }
    }
}
