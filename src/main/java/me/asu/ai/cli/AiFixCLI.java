package me.asu.ai.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.fix.FixCliOptions;
import me.asu.ai.fix.FixInteractiveSelector;
import me.asu.ai.fix.FixTargetMatcher;
import me.asu.ai.fix.GitWorktreeSupport;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.patch.PatchRetryExecutor;

public class AiFixCLI {

    public static void main(String[] args) throws Exception {
        String explicitConfigPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(explicitConfigPath);
        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }
        if (isConfigDebugEnabled(config, args)) {
            config.printDebugSummary();
        }

        FixCliOptions options = FixCliOptions.parse(args, config);
        if (options.task == null || options.task.isBlank() || "-".equals(options.task.trim())) {
            options.task = readTaskInteractively();
        }
        if (options.task == null || options.task.isBlank()) {
            System.out.println("Task is required");
            return;
        }
        options.normalize();
        if (options.printConfigOnly) {
            config.printDebugSummary();
            return;
        }

        GitWorktreeSupport git = new GitWorktreeSupport();
        FixTargetMatcher matcher = new FixTargetMatcher();
        git.ensureGitRepo();

        if (options.useStash) {
            git.autoStash();
        } else {
            git.ensureWorkingTreeClean();
        }

        if (!options.dryRun) {
            if (options.branch == null) {
                options.branch = "ai-fix/" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .format(LocalDateTime.now());
            }
            git.checkoutNewBranch(options.branch);
        }

        if (options.commitMessage == null) {
            options.commitMessage = "AI fix: " + options.task;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<MethodInfo> index = mapper.readValue(
                new File("index.json"),
                new TypeReference<List<MethodInfo>>() {
                });

        List<MethodInfo> targets = matcher.filterTargets(
                index,
                options.matchQuery,
                options.methodName,
                options.packageFilter,
                options.classFilter,
                options.fileFilter,
                options.callFilter,
                options.annotationFilter);

        if (targets.isEmpty()) {
            System.out.println("No matching methods found");
            return;
        }

        if (targets.size() > 1 && !options.noSelect) {
            FixInteractiveSelector selector = new FixInteractiveSelector(
                    config,
                    options.pageSize,
                    options.previewLines,
                    matcher);
            targets = selector.selectTargets(targets, options.limit, options.matchQuery);
        } else {
            targets = targets.stream().limit(options.limit).collect(Collectors.toList());
        }

        if (targets.isEmpty()) {
            System.out.println("No targets selected");
            return;
        }

        System.out.println("Targets: " + targets.size());

        ProjectSummary projectSummary = loadProjectSummary(options.projectSummaryPath, mapper);
        LLMClient llm = LLMFactory.create(options.provider, options.model, config);

        PatchRetryExecutor executor = new PatchRetryExecutor(
                llm,
                options.model,
                options.maxRetry,
                options.dryRun,
                config,
                projectSummary);

        int success = 0;
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();

        for (MethodInfo target : targets) {
            System.out.println("\n=== Processing: " + target.methodName + " ===");

            PatchRetryExecutor.PatchResult result = executor.execute(options.task, target);

            if (result.success()) {
                success++;
                successList.add(result.methodName());
                System.out.println("Applied: " + result.patchFile());
            } else {
                failedList.add(result.methodName());
                System.out.println("Failed: " + result.methodName());
                System.out.println(result.error());
            }
        }

        if (!options.dryRun && success > 0) {
            git.commitAll(options.commitMessage + " (" + success + " methods)");
        }

        if (options.useStash && !options.dryRun) {
            git.popStashQuietly();
        }

        System.out.println("\nSuccess: " + successList);
        System.out.println("Failed: " + failedList);
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar fix --task "<task>" [options]

                Options:
                  --task <text>               Describe the change; omit or use - to enter interactively
                  --match <words>             Fuzzy match package/class/method/file words
                  --method <methodName>       Filter by method name or fragment
                  --package <packageName>     Filter by package name or fragment
                  --class <className>         Filter by class name or fragment
                  --file <pathFragment>       Filter by source file path fragment
                  --call <methodCallName>     Filter by called method name
                  --annotation <annotation>   Filter by annotation name
                  --limit <number>            Limit matched methods
                  --page-size <number>        Interactive candidate count per page
                  --preview-lines <number>    Candidate preview line count
                  --branch <branchName>       Use a custom git branch
                  --commit-message <message>  Use a custom git commit message
                  --provider <provider>       openai | groq | ollama
                  --model <modelName>         Override configured model
                  --config <path>             Load an explicit config file
                  --project-summary <path>    Load project summary context JSON
                  --no-select                 Do not prompt when multiple methods match
                  --print-config              Print resolved config and exit
                  --dry-run                   Generate patch only, do not apply
                  --stash                     Auto-stash local changes before applying
                  --help                      Show this help
                  --config-debug              Print config loading summary

                Interactive selection:
                  When multiple methods match, you can:
                  - enter numbers like 1 or 1,3 to choose candidates
                  - enter * to choose all currently matched results
                  - enter b to go back to the previous filtered list
                  - enter n / p to move to the next / previous page
                  - enter g <page> to jump to a specific page
                  - enter q to quit without selecting
                  - press Enter to use the first N matches
                  - enter more words to narrow the list again
                """);
    }

    private static String readTaskInteractively() {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.print("Enter task: ");
        String line = scanner.nextLine();
        return line == null ? null : line.trim();
    }

    private static boolean containsHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigDebugEnabled(AppConfig config, String[] args) {
        for (String arg : args) {
            if ("--config-debug".equals(arg)) {
                return true;
            }
        }
        return "true".equalsIgnoreCase(config.get("config.debug"))
                || "1".equals(config.get("config.debug"));
    }

    private static String findOptionValue(String[] args, String optionName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static ProjectSummary loadProjectSummary(String projectSummaryPath, ObjectMapper mapper) throws Exception {
        Path path = projectSummaryPath == null || projectSummaryPath.isBlank()
                ? Paths.get("project-summary.json")
                : Paths.get(projectSummaryPath);
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            return null;
        }
        return mapper.readValue(normalized.toFile(), ProjectSummary.class);
    }

}
