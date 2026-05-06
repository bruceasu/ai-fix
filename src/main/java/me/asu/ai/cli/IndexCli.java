package me.asu.ai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import me.asu.ai.analyze.ProjectLanguage;
import me.asu.ai.analyze.ProjectLanguageDetector;
import me.asu.ai.index.JavaProjectIndexer;
import me.asu.ai.index.ProjectIndexer;
import me.asu.ai.index.ProjectIndexerFactory;
import me.asu.ai.model.MethodInfo;

public class IndexCli {

    public static List<MethodInfo> build(Path root) throws Exception {
        ProjectLanguage language = ProjectLanguageDetector.detect(root);
        ProjectIndexer indexer = ProjectIndexerFactory.create(language);
        return indexer.build(root);
    }

    public static List<MethodInfo> build(Path root, ProjectLanguage language) throws Exception {
        ProjectIndexer indexer = ProjectIndexerFactory.create(language);
        return indexer.build(root);
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }
        String languageOption = findOptionValue(args, "--language");
        Path root = Paths.get(findSourceRoot(args));
        ProjectLanguage language = ProjectLanguageDetector.parseOrDetect(languageOption, root);
        System.out.println("Detected language: " + language.name().toLowerCase());
        List<MethodInfo> index = build(root, language);

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
                  Scan Java, Go, or Python source files and generate index.json in the current working directory.

                Options:
                  --language <name>         auto | java | go | python
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

    private static String findOptionValue(String[] args, String optionName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static String findSourceRoot(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--language".equals(args[i])) {
                i++;
                continue;
            }
            if (!args[i].startsWith("--")) {
                return args[i];
            }
        }
        return ".";
    }
}
