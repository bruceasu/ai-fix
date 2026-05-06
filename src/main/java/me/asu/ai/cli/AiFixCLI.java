package me.asu.ai.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.fix.FixCliOptions;
import me.asu.ai.fix.FixInteractiveSelector;
import me.asu.ai.fix.FixSuggestionGenerator;
import me.asu.ai.fix.FixTargetMatcher;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.patch.PatchRetryExecutor;
import me.asu.ai.util.GitWorktreeSupport;
import me.asu.ai.util.JacksonUtils;
import me.asu.ai.util.Utils;

public class AiFixCLI {

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        if (Utils.containsHelpFlag(args)) {
            printUsage();
            return;
        }
        String explicitConfigPath = Utils.findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(explicitConfigPath);
        if (FixCliOptions.isConfigDebugEnabled(config, args)) {
            config.printDebugSummary();
        }

        FixCliOptions options = FixCliOptions.parse(args, config);
        options.normalize();
        if (options.printConfigOnly) {
            config.printDebugSummary();
            return;
        }
        if (options.task == null || options.task.isBlank() || "-".equals(options.task.trim())) {
            options.task = Utils.readTaskInteractively();
        }
        if (options.task == null || options.task.isBlank()) {
            System.out.println("Task is required");
            return;
        }

        GitWorktreeSupport git = new GitWorktreeSupport();
        FixTargetMatcher matcher = new FixTargetMatcher();
        List<MethodInfo> index = JacksonUtils.deserialize(
                Files.newBufferedReader(Paths.get("index.json"), StandardCharsets.UTF_8),
                new TypeReference<List<MethodInfo>>() { });

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

        ProjectSummary projectSummary = loadProjectSummary(options.projectSummaryPath);
        LLMClient llm = LLMFactory.create(options.provider, options.model, config);

        PatchRetryExecutor executor = new PatchRetryExecutor(
                llm,
                options.model,
                options.maxRetry,
                options.dryRun,
                config,
                projectSummary,
                options.verifyCmd);
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
        FixCliOptions.printUsage();
    }

    static String defaultBranchName() {
        return "ai-fix/" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now());
    }

    static String buildDefaultCommitMessage(String task) {
        return "AI fix: " + task;
    }

    private static ProjectSummary loadProjectSummary(String projectSummaryPath) throws Exception {
        Path path = projectSummaryPath == null || projectSummaryPath.isBlank()
                ? Paths.get("project-summary.json")
                : Paths.get(projectSummaryPath);
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            return null;
        }
        return JacksonUtils.deserialize(normalized.toFile(), ProjectSummary.class);
    }

}
