package me.asu.ai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import me.asu.ai.model.MethodInfo;

public class IndexCli {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    public static List<MethodInfo> build(Path root) throws Exception {
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
                                info.file = normalizedRoot.relativize(p.toAbsolutePath().normalize()).toString();
                                info.className = cls.getNameAsString();
                                info.methodName = m.getNameAsString();
                                info.signature = m.getDeclarationAsString();

                                info.annotations = new ArrayList<>();
                                m.getAnnotations().forEach(a ->
                                        info.annotations.add(a.getNameAsString())
                                );

                                info.calls = new ArrayList<>();
                                m.findAll(MethodCallExpr.class).forEach(call ->
                                        info.calls.add(call.getNameAsString())
                                );

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

    public static void main(String[] args) throws Exception {
        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        List<MethodInfo> index = build(root);

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File("index.json"), index);

        System.out.println("index.json generated");
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar index [source-root]

                Description:
                  Scan Java source files and generate index.json in the current working directory.

                Options:
                  --help   Show this help
                """);
    }

    private static boolean containsHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
