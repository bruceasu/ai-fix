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
import me.asu.ai.fix.FixSuggestionGenerator;
import me.asu.ai.fix.FixTargetMatcher;
import me.asu.ai.fix.GitWorktreeSupport;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.patch.PatchRetryExecutor;

public class AiFixCLI {

    public static void main(String[] args) throws Exception {
        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }
        String explicitConfigPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(explicitConfigPath);
        if (isConfigDebugEnabled(config, args)) {
            config.printDebugSummary();
        }

        FixCliOptions options = FixCliOptions.parse(args, config);
        options.normalize();
        if (options.printConfigOnly) {
            config.printDebugSummary();
            return;
        }
        if (options.task == null || options.task.isBlank() || "-".equals(options.task.trim())) {
            options.task = readTaskInteractively();
        }
        if (options.task == null || options.task.isBlank()) {
            System.out.println("Task is required");
            return;
        }

        GitWorktreeSupport git = new GitWorktreeSupport();
        FixTargetMatcher matcher = new FixTargetMatcher();
        ObjectMapper mapper = new ObjectMapper();
        List<MethodInfo> index = mapper.readValue(
                new File("index.json"),
                new TypeReference<List<MethodInfo>>() {
                });

        List<MethodInfo> targets = matcher.filterTargets(
                index,
                options.matchQuery,
                options.symbolName != null ? options.symbolName : options.methodName,
                options.packageFilter,
                options.containerFilter != null ? options.containerFilter : options.classFilter,
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
        FixSuggestionGenerator suggestionGenerator = new FixSuggestionGenerator(llm, config, projectSummary);
        ProblemKnowledgeStore knowledgeStore = new ProblemKnowledgeStore(config);

        int success = 0;
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        boolean shouldPopStash = false;
        boolean suggestionOnlyMode = options.suggestOnly;
        try {
            boolean isGitRepo = git.isGitRepo();
            if (!options.dryRun && !options.suggestOnly && isGitRepo) {
                if (git.hasUncommittedChanges()) {
                    System.out.println("Detected uncommitted files in the current Git branch.");
                    System.out.println("Please commit or stash your changes before allowing automatic modification.");
                    System.out.println("Fallback: switch to suggestion-only mode for this run.");
                    options.dryRun = true;
                    suggestionOnlyMode = true;
                }
            }

            if (!options.dryRun && !suggestionOnlyMode) {
                git.ensureGitRepo();
                if (options.useStash) {
                    git.autoStash();
                    shouldPopStash = true;
                }
                if (options.branch == null) {
                    options.branch = defaultBranchName();
                }
                git.checkoutNewBranch(options.branch);
                System.out.println("Using branch: " + options.branch);
            } else if (isGitRepo) {
                git.ensureGitRepo();
            } else if (suggestionOnlyMode) {
                System.out.println("Git repository was not available; returning suggestions only.");
            }

            if (options.commitMessage == null) {
                options.commitMessage = buildDefaultCommitMessage(options.task);
            }

            for (MethodInfo target : targets) {
                System.out.println("\n=== Processing: " + target.methodName + " ===");

                if (suggestionOnlyMode) {
                    String suggestion = suggestionGenerator.generate(options.task, target);
                    success++;
                    successList.add(target.methodName);
                    System.out.println(suggestion);
                    knowledgeStore.recordFixSuggestion(options.task, target, suggestion);
                    continue;
                }

                PatchRetryExecutor.PatchResult result = executor.execute(options.task, target);

                if (result.success()) {
                    success++;
                    successList.add(result.methodName());
                    System.out.println("Applied: " + result.patchFile());
                    knowledgeStore.recordFixExecution(options.task, target, true, "", options.branch, result.patchFile());
                } else {
                    failedList.add(result.methodName());
                    System.out.println("Failed: " + result.methodName());
                    System.out.println(result.error());
                    knowledgeStore.recordFixExecution(options.task, target, false, result.error(), options.branch);
                }
            }

            if (!options.dryRun && success > 0) {
                git.commitAll(options.commitMessage + " (" + success + " methods)");
            }
        } finally {
            if (shouldPopStash) {
                git.popStashQuietly();
            }
        }

        System.out.println("\nSuccess: " + successList);
        System.out.println("Failed: " + failedList);
        if (suggestionOnlyMode) {
            System.out.println("Result mode: suggestion-only (no working tree files were modified).");
        }
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar fix --task "<task>" [options]

                Options:
                  --task <text>               Describe the change; omit or use - to enter interactively
                  --match <words>             Fuzzy match package/class/method/file words
                  --method <methodName>       Filter by method name or fragment
                  --symbol <symbolName>       Filter by language-neutral symbol name or fragment
                  --package <packageName>     Filter by package name or fragment
                  --class <className>         Filter by class name or fragment
                  --container <name>          Filter by language-neutral container/module/class name
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
                  --suggest-only              Generate structured repair advice only, never modify files
                  --print-config              Print resolved config and exit
                  --dry-run                   Generate patch only, do not apply
                  --stash                     Auto-stash local changes before applying
                  --help                      Show this help
                  --config-debug              Print config loading summary

                Git behavior:
                  - when inside a Git repository and the working tree is clean, fix auto-creates a new branch before modifying files
                  - when uncommitted files are detected, fix does not modify the working tree and falls back to suggestion-only mode
                  - suggestion-only mode prints structured repair advice instead of applying a patch

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

    static String defaultBranchName() {
        return "ai-fix/" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now());
    }

    static String buildDefaultCommitMessage(String task) {
        return "AI fix: " + task;
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
