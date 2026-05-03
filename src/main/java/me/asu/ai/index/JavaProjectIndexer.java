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

public class JavaProjectIndexer implements ProjectIndexer {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    @Override
    public List<MethodInfo> build(Path root) throws Exception {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<MethodInfo> list = new ArrayList<>();

        Files.walk(normalizedRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(p);

                        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                            cls.findAll(MethodDeclaration.class).forEach(m -> {
                                MethodInfo info = new MethodInfo();
                                info.projectRoot = ".";
                                info.language = "java";
                                info.file = normalizedRoot.relativize(p.toAbsolutePath().normalize()).toString();
                                info.className = cls.getNameAsString();
                                info.symbolType = "method";
                                info.containerName = cls.getNameAsString();
                                info.methodName = m.getNameAsString();
                                info.symbolName = m.getNameAsString();
                                info.signature = m.getDeclarationAsString();

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

        return list;
    }
}
