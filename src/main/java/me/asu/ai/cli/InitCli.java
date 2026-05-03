package me.asu.ai.cli;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class InitCli {

    public static void main(String[] args) throws Exception {
        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }

        Path configDir = Paths.get(System.getProperty("user.home"), ".config");
        Files.createDirectories(configDir);

        String configTemplate = loadTemplate("ai-fix.properties.example", defaultConfigTemplate());
        String promptTemplate = loadTemplate("ai-fix-prompt.txt", defaultPromptTemplate());
        String promptFixTemplate = loadTemplate("ai-fix-prompt-fix.txt", defaultPromptFixTemplate());

        System.out.println("Initializing config directory: " + configDir);
        System.out.println("The following files will be managed:");
        System.out.println("- " + configDir.resolve("ai-fix.properties"));
        System.out.println("- " + configDir.resolve("ai-fix-prompt.txt"));
        System.out.println("- " + configDir.resolve("ai-fix-prompt-fix.txt"));
        System.out.println();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            manageFile(scanner, configDir.resolve("ai-fix.properties"), configTemplate, "Config file");
            manageFile(scanner, configDir.resolve("ai-fix-prompt.txt"), promptTemplate, "Main prompt template");
            manageFile(scanner, configDir.resolve("ai-fix-prompt-fix.txt"), promptFixTemplate, "Patch retry prompt template");
        }

        printEnvironmentGuide(configDir);
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar init

                Description:
                  Interactively create or update ~/.config/ai-fix.properties
                  and the prompt template files used by ai-fix.

                Behavior:
                  - If a file exists, its current content is shown first
                  - Before modify or delete, a backup file is created
                  - You can keep, delete, or modify each file

                Options:
                  --help   Show this help
                """);
    }

    private static void manageFile(Scanner scanner, Path path, String template, String label) throws IOException {
        System.out.println("=== " + label + " ===");
        if (Files.exists(path)) {
            String current = Files.readString(path, StandardCharsets.UTF_8);
            System.out.println("Current file exists: " + path);
            System.out.println("----- Current content start -----");
            System.out.println(current);
            System.out.println("----- Current content end -----");
            String action = askChoice(
                    scanner,
                    "Choose an action: [K]eep, [D]elete, [M]odify",
                    "K", "D", "M");
            switch (action) {
                case "K" -> System.out.println("Kept: " + path);
                case "D" -> {
                    Path backup = backupFile(path);
                    Files.delete(path);
                    System.out.println("Backup created: " + backup);
                    System.out.println("Deleted: " + path);
                }
                case "M" -> {
                    Path backup = backupFile(path);
                    System.out.println("Backup created: " + backup);
                    writeManagedFile(scanner, path, template);
                }
                default -> throw new IllegalStateException("Unexpected action: " + action);
            }
        } else {
            String action = askChoice(
                    scanner,
                    "File does not exist. Create it? [Y]es, [N]o",
                    "Y", "N");
            if ("Y".equals(action)) {
                writeManagedFile(scanner, path, template);
            } else {
                System.out.println("Skipped: " + path);
            }
        }
        System.out.println();
    }

    private static void writeManagedFile(Scanner scanner, Path path, String template) throws IOException {
        String mode = askChoice(
                scanner,
                "Choose write mode: [T]emplate, [P]aste custom content",
                "T", "P");
        String content = "T".equals(mode) ? template : readMultilineContent(scanner);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("Written: " + path);
    }

    private static Path backupFile(Path path) throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        Path backup = path.resolveSibling(path.getFileName() + ".bak." + timestamp);
        Files.copy(path, backup);
        return backup;
    }

    private static String readMultilineContent(Scanner scanner) {
        System.out.println("Paste the full content. Enter EOF on a line by itself to finish:");
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = scanner.nextLine();
            if ("EOF".equals(line)) {
                break;
            }
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String askChoice(Scanner scanner, String prompt, String... allowedValues) {
        while (true) {
            System.out.print(prompt + " ");
            String input = scanner.nextLine().trim().toUpperCase();
            for (String value : allowedValues) {
                if (value.equalsIgnoreCase(input)) {
                    return value.toUpperCase();
                }
            }
            System.out.println("Invalid input. Please try again.");
        }
    }

    private static void printEnvironmentGuide(Path configDir) {
        System.out.println("Initialization complete.");
        System.out.println();
        System.out.println("You can also configure these environment variables:");
        System.out.println("- AI_FIX_PROVIDER");
        System.out.println("- AI_FIX_MODEL");
        System.out.println("- OPENAI_API_KEY");
        System.out.println("- OPENAI_BASE_URL");
        System.out.println("- GROQ_API_KEY");
        System.out.println("- GROQ_BASE_URL");
        System.out.println("- OLLAMA_BASE_URL");
        System.out.println("- AI_FIX_PROMPT_TEMPLATE_FILE");
        System.out.println("- AI_FIX_PROMPT_FIX_TEMPLATE_FILE");
        System.out.println();
        System.out.println("Windows PowerShell example:");
        System.out.println("  $env:GROQ_API_KEY=\"your_groq_key\"");
        System.out.println("  $env:AI_FIX_PROVIDER=\"groq\"");
        System.out.println("  $env:AI_FIX_MODEL=\"llama-3.1-8b-instant\"");
        System.out.println();
        System.out.println("Windows persistent setx example:");
        System.out.println("  setx GROQ_API_KEY \"your_groq_key\"");
        System.out.println("  setx AI_FIX_PROVIDER \"groq\"");
        System.out.println("  setx AI_FIX_MODEL \"llama-3.1-8b-instant\"");
        System.out.println();
        System.out.println("Linux/macOS bash example:");
        System.out.println("  export GROQ_API_KEY=\"your_groq_key\"");
        System.out.println("  export AI_FIX_PROVIDER=\"groq\"");
        System.out.println("  export AI_FIX_MODEL=\"llama-3.1-8b-instant\"");
        System.out.println();
        System.out.println("Suggested config file location:");
        System.out.println("  " + configDir.resolve("ai-fix.properties"));
    }

    private static String loadTemplate(String fileName, String fallback) {
        try {
            Path path = resolveProgramDirectory().resolve(fileName);
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Path resolveProgramDirectory() {
        try {
            Path jarPath = Paths.get(
                    InitCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File file = jarPath.toFile();
            return file.isDirectory() ? file.toPath() : file.getParentFile().toPath();
        } catch (URISyntaxException e) {
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    private static boolean containsHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String defaultConfigTemplate() {
        return """
                provider=groq
                model=llama-3.1-8b-instant

                openai.api.key=your_openai_key
                openai.base.url=https://api.openai.com/v1

                groq.api.key=your_groq_key
                groq.base.url=https://api.groq.com/openai/v1

                ollama.base.url=http://localhost:11434

                prompt.template.file=ai-fix-prompt.txt
                prompt.fix-template.file=ai-fix-prompt-fix.txt
                """;
    }

    private static String defaultPromptTemplate() {
        return """
                You are a senior Java engineer.

                Task:
                {task}

                Target:
                - File: {file}
                - Class: {className}
                - Method: {methodName}
                - Signature: {signature}
                - Lines: {beginLine}-{endLine}

                Current code:
                ```java
                {code}
                ```
                Rules:

                Output ONLY unified diff.
                No explanation.
                The diff must be applicable by git apply.
                Use the file path exactly as shown: {file}

                Keep changes minimal.
                """;
    }

    private static String defaultPromptFixTemplate() {
        return """
                You are fixing a failed git patch.

                Original task:
                {task}

                Target:

                File: {file}
                Class: {className}
                Method: {methodName}
                Signature: {signature}

                Current latest code:

                {code}

                The previous patch failed to apply.

                Git error:

                {error}

                Failed patch:

                {failedDiff}

                Task:
                Fix the patch so it applies cleanly to the current latest code.

                Rules:

                Output ONLY corrected unified diff.
                No explanation.
                Preserve the original intent.
                Fix context, line ranges, and patch format if needed.
                The diff must be applicable by git apply.

                Use the file path exactly as shown: {file}
                """;
    }
}
