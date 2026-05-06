package me.asu.ai.index;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.parse.JavaCodeParser;
import me.asu.ai.util.IgnoreFilter;

/**
 * JavaParser 后端的 Java 代码索引器。
 *
 * 使用 JavaParser 3.28.1 解析 Java 源文件，提取方法、字段、注解等元数据。
 * 此类实现 {@link ProjectIndexer} 和 {@link JavaCodeParser} 接口，支持向后兼容和新的统一接口。
 *
 * @see JavaCodeParser
 * @see ProjectIndexer
 */
public class JavaProjectIndexer implements ProjectIndexer, JavaCodeParser {


    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25);
    }

    private final IgnoreFilter ignoreFilter;

    /**
     * 构造函数，使用默认配置。
     */
    public JavaProjectIndexer() {
        this.ignoreFilter = null;
    }

    /**
     * 构造函数，为项目根目录初始化忽略过滤器。
     *
     * @param projectRoot 项目根目录，用于初始化忽略过滤器
     */
    public JavaProjectIndexer(Path projectRoot) {
        this.ignoreFilter = new IgnoreFilter(projectRoot);
    }

    /**
     * 实现 ProjectIndexer 接口：索引整个项目。
     * 此方法保留以支持向后兼容。
     *
     * @param root 项目根目录
     * @return 项目中所有方法的元数据列表
     * @throws Exception 如果解析失败
     */
    @Override
    public List<MethodInfo> build(Path root) throws Exception {
        return indexProject(root);
    }

    /**
     * 实现 JavaCodeParser 接口：索引单个 Java 文件。
     *
     * @param javaFile Java 源文件路径
     * @return 文件中所有方法的元数据列表
     * @throws Exception 如果解析失败或文件读取错误
     */
    @Override
    public List<MethodInfo> indexFile(Path javaFile) throws Exception {
        List<MethodInfo> list = new ArrayList<>();
        try {
          CompilationUnit cu = StaticJavaParser.parse(javaFile);
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Find all types: classes, interfaces, enums, records, annotations
            for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                String className = type.getNameAsString();

                List<String> classFields = type.findAll(FieldDeclaration.class).stream()
                        .map(f -> f.toString().trim())
                        .collect(Collectors.toList());

                // Find all callables: methods and constructors
                for (CallableDeclaration<?> callable : type.findAll(CallableDeclaration.class)) {
                    MethodInfo info = buildMethodInfo(javaFile, packageName, className, classFields, callable);
                    list.add(info);
                }
            }

        } catch (Exception e) {
            System.err.println("Parse failed: " + javaFile + " - " + e.getMessage());
        }

        return list;
    }

    /**
     * 实现 JavaCodeParser 接口：索引整个项目。
     *
     * @param projectRoot 项目根目录
     * @return 项目中所有方法的元数据列表
     * @throws Exception 如果遍历或解析失败
     */
    @Override
    public List<MethodInfo> indexProject(Path projectRoot) throws Exception {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<MethodInfo> list = Collections.synchronizedList(new ArrayList<>());
        IgnoreFilter filter = new IgnoreFilter(normalizedRoot);

        List<Path> javaFiles;
        try (var walk = Files.walk(normalizedRoot)) {
            javaFiles = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !filter.shouldIgnore(p))
                    .collect(Collectors.toList());
        }

        System.out.println("Found " + javaFiles.size() + " Java files to index. Backend: " + getBackendName());

        javaFiles.parallelStream().forEach(p -> {
            try {
                List<MethodInfo> fileResults = indexFileInternal(normalizedRoot, p);
                list.addAll(fileResults);
            } catch (Exception e) {
                System.err.println("Parse failed: " + p + " - " + e.getMessage());
            }
        });

        return new ArrayList<>(list);
    }

    /**
     * 实现 JavaCodeParser 接口：分析项目（此方法委托给 ProjectAnalyzer）。
     * 注意：此索引器不提供项目分析功能，相关功能由 JavaProjectAnalyzer 提供。
     *
     * @param projectRoot 项目根目录
     * @return 项目总结信息
     * @throws Exception 不支持的操作异常
     */
    @Override
    public me.asu.ai.model.ProjectSummary analyzeProject(Path projectRoot) throws Exception {
        throw new UnsupportedOperationException("Use JavaProjectAnalyzer for project analysis");
    }

    @Override
    public String getBackendName() {
        return "JavaParser 3.28.1";
    }

    @Override
    public boolean isAvailable() {
        return true; // JavaParser 总是通过 Maven 依赖可用
    }

    /**
     * 内部方法：解析单个文件并提取方法信息。
     *
     * @param normalizedRoot 规范化的项目根目录
     * @param javaFile Java 源文件路径
     * @return 文件中所有方法的元数据列表
     */
    private List<MethodInfo> indexFileInternal(Path normalizedRoot, Path javaFile) throws Exception {
        List<MethodInfo> list = new ArrayList<>();
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            String className = type.getNameAsString();

            List<String> classFields = type.findAll(FieldDeclaration.class).stream()
                    .map(f -> f.toString().trim())
                    .collect(Collectors.toList());

            for (CallableDeclaration<?> callable : type.findAll(CallableDeclaration.class)) {
                MethodInfo info = new MethodInfo();
                info.projectRoot = ".";
                info.language = "java";
                info.file = normalizedRoot.relativize(javaFile.toAbsolutePath().normalize()).toString().replace("\\", "/");
                info.packageName = packageName;
                info.className = className;
                info.containerName = className;
                info.methodName = callable.getNameAsString();
                info.symbolName = callable.getNameAsString();

                if (callable instanceof MethodDeclaration m) {
                    info.symbolType = "method";
                    info.signature = m.getDeclarationAsString();
                } else if (callable instanceof ConstructorDeclaration c) {
                    info.symbolType = "constructor";
                    info.signature = c.getDeclarationAsString();
                }

                info.fields = classFields;

                callable.getComment().ifPresent(comment -> info.javadoc = comment.getContent());

                info.annotations = new ArrayList<>();
                for (AnnotationExpr a : callable.getAnnotations()) {
                    info.annotations.add(a.getNameAsString());
                }

                info.calls = new ArrayList<>();
                for (MethodCallExpr call : callable.findAll(MethodCallExpr.class)) {
                    info.calls.add(call.getNameAsString());
                }

                callable.getRange().ifPresent(r -> {
                    info.beginLine = r.begin.line;
                    info.endLine = r.end.line;
                });

                list.add(info);
            }
        }
        return list;
    }

    /**
     * 辅助方法：为给定的 Callable 构建 MethodInfo 对象。
     *
     * @param javaFile Java 源文件路径
     * @param packageName 包名
     * @param className 类名
     * @param classFields 类字段列表
     * @param callable 可调用声明（方法或构造函数）
     * @return 构建的 MethodInfo 对象
     */
    private MethodInfo buildMethodInfo(Path javaFile, String packageName, String className,
                                      List<String> classFields, CallableDeclaration<?> callable) {
        MethodInfo info = new MethodInfo();
        info.projectRoot = ".";
        info.language = "java";
        info.file = javaFile.toString().replace("\\", "/");
        info.packageName = packageName;
        info.className = className;
        info.containerName = className;
        info.methodName = callable.getNameAsString();
        info.symbolName = callable.getNameAsString();

        if (callable instanceof MethodDeclaration m) {
            info.symbolType = "method";
            info.signature = m.getDeclarationAsString();
        } else if (callable instanceof ConstructorDeclaration c) {
            info.symbolType = "constructor";
            info.signature = c.getDeclarationAsString();
        }

        info.fields = classFields;

        callable.getComment().ifPresent(comment -> info.javadoc = comment.getContent());

        info.annotations = new ArrayList<>();
        for (AnnotationExpr a : callable.getAnnotations()) {
            info.annotations.add(a.getNameAsString());
        }

        info.calls = new ArrayList<>();
        for (MethodCallExpr call : callable.findAll(MethodCallExpr.class)) {
            info.calls.add(call.getNameAsString());
        }

        callable.getRange().ifPresent(r -> {
            info.beginLine = r.begin.line;
            info.endLine = r.end.line;
        });

        return info;
    }
}
