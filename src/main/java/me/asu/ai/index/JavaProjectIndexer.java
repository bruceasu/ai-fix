package me.asu.ai.index;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.asu.ai.model.MethodInfo;

import com.github.javaparser.ast.body.FieldDeclaration;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import me.asu.ai.util.IgnoreFilter;

public class JavaProjectIndexer implements ProjectIndexer {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    @Override
    public List<MethodInfo> build(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<MethodInfo> list = Collections.synchronizedList(new ArrayList<>());
        IgnoreFilter filter = new IgnoreFilter(normalizedRoot);

        List<Path> javaFiles = Files.walk(normalizedRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !filter.shouldIgnore(p))
                .collect(Collectors.toList());

        System.out.println("Found " + javaFiles.size() + " Java files to index.");

        javaFiles.parallelStream().forEach(p -> {
            try {
                CompilationUnit cu = StaticJavaParser.parse(p);
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString())
                        .orElse("");

                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                    List<String> classFields = cls.findAll(FieldDeclaration.class).stream()
                            .map(f -> f.toString().trim())
                            .collect(Collectors.toList());

                    cls.findAll(MethodDeclaration.class).forEach(m -> {
                        MethodInfo info = new MethodInfo();
                        info.projectRoot = ".";
                        info.language = "java";
                        info.file = normalizedRoot.relativize(p.toAbsolutePath().normalize()).toString().replace("\\", "/");
                        info.packageName = packageName;
                        info.className = cls.getNameAsString();
                        info.symbolType = "method";
                        info.containerName = cls.getNameAsString();
                        info.methodName = m.getNameAsString();
                        info.symbolName = m.getNameAsString();
                        info.signature = m.getDeclarationAsString();
                        info.fields = classFields;

                        m.getComment().ifPresent(comment -> info.javadoc = comment.getContent());

                        info.annotations = new ArrayList<>();
                        m.getAnnotations().forEach(a -> info.annotations.add(a.getNameAsString()));

                        info.calls = new ArrayList<>();
                        m.findAll(MethodCallExpr.class).forEach(call -> info.calls.add(call.getNameAsString()));

                        m.getRange().ifPresent(r -> {
                            info.beginLine = r.begin.line;
                            info.endLine = r.end.line;
                        });

                        list.add(info);
                    });
                });

            } catch (Exception e) {
                System.out.println("Parse failed: " + p + " - " + e.getMessage());
            }
        });

        return new ArrayList<>(list);
    }
}
