package me.asu.ai.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.patch.PatchRetryExecutor;

public class AiFixCLI {

    private static final int DEFAULT_INTERACTIVE_PAGE_SIZE = 10;

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

        String task = null;
        String matchQuery = "";
        String methodName = null;
        String packageFilter = null;
        String classFilter = null;
        String fileFilter = null;
        String callFilter = null;
        String annotationFilter = null;
        String branch = null;
        String commitMessage = null;
        String projectSummaryPath = null;
        String provider = config.get("provider", "openai");
        String model = config.get("model", "gpt-5-mini");

        int maxRetry = 2;
        int limit = 3;
        int pageSize = DEFAULT_INTERACTIVE_PAGE_SIZE;
        boolean dryRun = false;
        boolean noSelect = false;
        boolean useStash = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--task" -> task = args[++i];
                case "--match" -> matchQuery = matchQuery + " " + args[++i];
                case "--method" -> methodName = args[++i];
                case "--package" -> packageFilter = args[++i];
                case "--class" -> classFilter = args[++i];
                case "--file" -> fileFilter = args[++i];
                case "--call" -> callFilter = args[++i];
                case "--annotation" -> annotationFilter = args[++i];
                case "--limit" -> limit = Integer.parseInt(args[++i]);
                case "--page-size" -> pageSize = Integer.parseInt(args[++i]);
                case "--branch" -> branch = args[++i];
                case "--commit-message" -> commitMessage = args[++i];
                case "--model" -> model = args[++i];
                case "--provider" -> provider = args[++i];
                case "--project-summary" -> projectSummaryPath = args[++i];
                case "--config" -> i++;
                case "--dry-run" -> dryRun = true;
                case "--no-select" -> noSelect = true;
                case "--stash" -> useStash = true;
                default -> {
                    matchQuery =  matchQuery + " " + args[i];
                }
            }
        }

        if (task == null || task.isBlank() || "-".equals(task.trim())) {
            task = readTaskInteractively();
        }
        if (task == null || task.isBlank()) {
            System.out.println("Task is required");
            return;
        }
        if (pageSize <= 0) {
            System.out.println("--page-size must be greater than 0, use the default value: "
                 + DEFAULT_INTERACTIVE_PAGE_SIZE);
            pageSize = DEFAULT_INTERACTIVE_PAGE_SIZE;
            return;
        }

        ensureGitRepo();

        if (useStash) {
            autoStash();
        } else {
            ensureWorkingTreeClean();
        }

        if (!dryRun) {
            if (branch == null) {
                branch = "ai-fix/" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .format(LocalDateTime.now());
            }
            runCommand("git", "checkout", "-b", branch);
        }

        if (commitMessage == null) {
            commitMessage = "AI fix: " + task;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<MethodInfo> index = mapper.readValue(
                new File("index.json"),
                new TypeReference<List<MethodInfo>>() {
                });

        List<MethodInfo> targets = filterTargets(
                index,
                matchQuery,
                methodName,
                packageFilter,
                classFilter,
                fileFilter,
                callFilter,
                annotationFilter);

        if (targets.isEmpty()) {
            System.out.println("No matching methods found");
            return;
        }

        if (targets.size() > 1 && !noSelect) {
            targets = selectTargetsInteractively(targets, limit, pageSize, matchQuery);
        } else {
            targets = targets.stream().limit(limit).collect(Collectors.toList());
        }

        if (targets.isEmpty()) {
            System.out.println("No targets selected");
            return;
        }

        System.out.println("Targets: " + targets.size());

        ProjectSummary projectSummary = loadProjectSummary(projectSummaryPath, mapper);
        LLMClient llm = LLMFactory.create(provider, model, config);

        PatchRetryExecutor executor = new PatchRetryExecutor(
                llm,
                model,
                maxRetry,
                dryRun,
                config,
                projectSummary);

        int success = 0;
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();

        for (MethodInfo target : targets) {
            System.out.println("\n=== Processing: " + target.methodName + " ===");

            PatchRetryExecutor.PatchResult result = executor.execute(task, target);

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

        if (!dryRun && success > 0) {
            runCommand("git", "add", ".");
            runCommand("git", "commit", "-m",
                    commitMessage + " (" + success + " methods)");
        }

        if (useStash && !dryRun) {
            try {
                runCommand("git", "stash", "pop");
            } catch (Exception e) {
                System.out.println("stash pop failed");
            }
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
                  --branch <branchName>       Use a custom git branch
                  --commit-message <message>  Use a custom git commit message
                  --provider <provider>       openai | groq | ollama
                  --model <modelName>         Override configured model
                  --config <path>             Load an explicit config file
                  --project-summary <path>    Load project summary context JSON
                  --no-select                 Do not prompt when multiple methods match
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

    private static List<MethodInfo> filterTargets(
            List<MethodInfo> index,
            String matchQuery,
            String methodName,
            String packageFilter,
            String classFilter,
            String fileFilter,
            String callFilter,
            String annotationFilter) {
        List<MethodInfo> targets = applyMatchQuery(index, matchQuery);

        if (methodName != null) {
            String finalMethodName = methodName;
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(m.methodName, finalMethodName))
                    .collect(Collectors.toList());
        }

        if (packageFilter != null) {
            String finalPackageFilter = packageFilter;
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(inferPackageName(m), finalPackageFilter))
                    .collect(Collectors.toList());
        }

        if (classFilter != null) {
            String finalClassFilter = classFilter;
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(m.className, finalClassFilter))
                    .collect(Collectors.toList());
        }

        if (fileFilter != null) {
            String finalFileFilter = normalizePath(fileFilter);
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(normalizePath(m.file), finalFileFilter))
                    .collect(Collectors.toList());
        }

        if (callFilter != null) {
            String finalCallFilter = callFilter;
            targets = targets.stream()
                    .filter(m -> m.calls != null && m.calls.contains(finalCallFilter))
                    .collect(Collectors.toList());
        }

        if (annotationFilter != null) {
            String finalAnnotationFilter = annotationFilter;
            targets = targets.stream()
                    .filter(m -> m.annotations != null && m.annotations.contains(finalAnnotationFilter))
                    .collect(Collectors.toList());
        }

        return targets;
    }

    private static List<MethodInfo> applyMatchQuery(List<MethodInfo> index, String matchQuery) {
        if (matchQuery == null || matchQuery.isBlank()) {
            return index;
        }
        List<String> terms = splitTerms(matchQuery);
        return index.stream()
                .map(method -> new SearchHit(method, scoreMethod(method, terms)))
                .filter(hit -> hit.score > 0)
                .sorted(Comparator.comparingInt(SearchHit::score).reversed()
                        .thenComparing(hit -> safe(hit.method.className), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(hit -> safe(hit.method.methodName), String.CASE_INSENSITIVE_ORDER))
                .map(SearchHit::method)
                .collect(Collectors.toList());
    }

    private static List<MethodInfo> selectTargetsInteractively(
            List<MethodInfo> targets,
            int limit,
            int pageSize,
            String matchQuery) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        List<MethodInfo> currentTargets = new ArrayList<>(targets);
        Deque<SearchState> history = new LinkedList<>();
        String currentQuery = matchQuery == null ? "" : matchQuery.trim();
        int currentPage = 0;

        while (true) {
            int totalPages = pageCount(currentTargets.size(), pageSize);
            currentPage = clampPage(currentPage, totalPages);
            int pageStart = currentPage * pageSize;
            int pageEnd = Math.min(pageStart + pageSize, currentTargets.size());
            List<MethodInfo> visibleTargets = currentTargets.subList(pageStart, pageEnd);

            System.out.println("Matched multiple methods. Select target numbers separated by comma.");
            System.out.println("Showing page " + (currentPage + 1) + "/" + totalPages
                    + ", items " + (pageStart + 1) + "-" + pageEnd + " of " + currentTargets.size());
            for (int i = 0; i < visibleTargets.size(); i++) {
                int displayIndex = pageStart + i + 1;
                System.out.println(formatCandidateLine(displayIndex, visibleTargets.get(i), currentQuery));
            }
            System.out.println("Press Enter to use the first " + Math.min(limit, currentTargets.size()) + " matches.");
            System.out.println("Type * to use all currently matched results.");
            System.out.println("Type b to go back to the previous filtered list.");
            if (totalPages > 1) {
                System.out.println("Type n for next page, p for previous page.");
                System.out.println("Type g <page> to jump to a specific page.");
            }
            System.out.println("Type q to quit without selecting.");
            System.out.println("Or type more keywords to narrow the list, for example: cli main usage");
            System.out.print("Selection or filter: ");

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                return currentTargets.stream().limit(limit).collect(Collectors.toList());
            }
            if ("q".equalsIgnoreCase(line)) {
                return List.of();
            }
            if ("*".equals(line)) {
                return new ArrayList<>(currentTargets);
            }
            if ("b".equalsIgnoreCase(line)) {
                if (history.isEmpty()) {
                    System.out.println("Already at the initial result set.");
                    continue;
                }
                SearchState previous = history.removeLast();
                currentTargets = new ArrayList<>(previous.targets());
                currentQuery = previous.query();
                currentPage = previous.page();
                continue;
            }
            if ("n".equalsIgnoreCase(line)) {
                if (currentPage + 1 >= totalPages) {
                    System.out.println("Already at the last page.");
                } else {
                    currentPage++;
                }
                continue;
            }
            if ("p".equalsIgnoreCase(line)) {
                if (currentPage <= 0) {
                    System.out.println("Already at the first page.");
                } else {
                    currentPage--;
                }
                continue;
            }
            if (isGotoPageCommand(line)) {
                int targetPage = parseGotoPage(line);
                if (targetPage < 1 || targetPage > totalPages) {
                    System.out.println("Page out of range: " + targetPage);
                } else {
                    currentPage = targetPage - 1;
                }
                continue;
            }

            if (looksLikeSelection(line)) {
                List<MethodInfo> selected = parseSelection(line, currentTargets, limit);
                if (!selected.isEmpty()) {
                    return selected;
                }
                System.out.println("No valid selection. Try again.");
                continue;
            }

            List<MethodInfo> refined = applyMatchQuery(currentTargets, line);
            if (refined.isEmpty()) {
                System.out.println("No matches for filter: " + line);
                continue;
            }
            history.addLast(new SearchState(new ArrayList<>(currentTargets), currentQuery, currentPage));
            currentTargets = refined;
            currentQuery = line;
            currentPage = 0;
            if (currentTargets.size() == 1) {
                System.out.println("Refined to a single match.");
                return currentTargets;
            }
        }
    }

    private static String formatCandidateLine(int index, MethodInfo method, String currentQuery) {
        String scorePart = "";
        if (currentQuery != null && !currentQuery.isBlank()) {
            int score = scoreMethod(method, splitTerms(currentQuery));
            scorePart = "  score=" + score;
        }
        return String.format(
                Locale.ROOT,
                "%d. %s#%s%s  signature=%s  package=%s  file=%s  lines=%d-%d",
                index,
                safe(method.className),
                safe(method.methodName),
                scorePart,
                safe(method.signature),
                safe(inferPackageName(method)),
                safe(method.file),
                method.beginLine,
                method.endLine);
    }

    private static String inferPackageName(MethodInfo method) {
        if (method == null || method.file == null || method.file.isBlank()) {
            return "";
        }
        String normalized = normalizePath(method.file);
        int srcIndex = normalized.indexOf("/java/");
        String packagePath = srcIndex >= 0
                ? normalized.substring(srcIndex + "/java/".length())
                : normalized;
        int slashIndex = packagePath.lastIndexOf('/');
        if (slashIndex < 0) {
            return "";
        }
        return packagePath.substring(0, slashIndex).replace('/', '.');
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static boolean containsIgnoreCase(String source, String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static List<String> splitTerms(String matchQuery) {
        return List.of(matchQuery.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .stream()
                .filter(term -> !term.isBlank())
                .collect(Collectors.toList());
    }

    private static int scoreMethod(MethodInfo method, List<String> terms) {
        String packageName = inferPackageName(method).toLowerCase(Locale.ROOT);
        String className = safe(method.className).toLowerCase(Locale.ROOT);
        String methodName = safe(method.methodName).toLowerCase(Locale.ROOT);
        String fileName = normalizePath(safe(method.file)).toLowerCase(Locale.ROOT);
        String signature = safe(method.signature).toLowerCase(Locale.ROOT);
        String annotations = joinLower(method.annotations);
        String calls = joinLower(method.calls);
        String haystack = String.join(" ", packageName, className, methodName, fileName, signature, annotations, calls);

        int score = 0;
        for (String term : terms) {
            if (!haystack.contains(term)) {
                return 0;
            }
            score += scoreTerm(term, packageName, className, methodName, fileName, signature, annotations, calls);
        }
        return score;
    }

    private static int scoreTerm(
            String term,
            String packageName,
            String className,
            String methodName,
            String fileName,
            String signature,
            String annotations,
            String calls) {
        int score = 1;
        if (methodName.equals(term)) {
            score += 80;
        } else if (methodName.contains(term)) {
            score += 40;
        }
        if (className.equals(term)) {
            score += 60;
        } else if (className.contains(term)) {
            score += 30;
        }
        if (packageName.contains(term)) {
            score += 20;
        }
        if (fileName.contains(term)) {
            score += 20;
        }
        if (signature.contains(term)) {
            score += 10;
        }
        if (calls.contains(term)) {
            score += 8;
        }
        if (annotations.contains(term)) {
            score += 6;
        }
        return score;
    }

    private static String joinLower(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static boolean looksLikeSelection(String line) {
        for (String token : line.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.matches("\\d+")) {
                return false;
            }
        }
        return true;
    }

    private static List<MethodInfo> parseSelection(String line, List<MethodInfo> targets, int limit) {
        List<MethodInfo> selected = new ArrayList<>();
        for (String token : line.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int index = Integer.parseInt(trimmed);
            if (index < 1 || index > targets.size()) {
                System.out.println("Skip invalid selection: " + trimmed);
                continue;
            }
            MethodInfo candidate = targets.get(index - 1);
            if (!selected.contains(candidate)) {
                selected.add(candidate);
            }
        }
        return selected.stream().limit(limit).collect(Collectors.toList());
    }

    private static int pageCount(int itemCount, int pageSize) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private static int clampPage(int currentPage, int totalPages) {
        if (currentPage < 0) {
            return 0;
        }
        if (currentPage >= totalPages) {
            return totalPages - 1;
        }
        return currentPage;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String readTaskInteractively() {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.print("Enter task: ");
        String line = scanner.nextLine();
        return line == null ? null : line.trim();
    }

    private static boolean isGotoPageCommand(String line) {
        return line != null && line.trim().matches("(?i)g\\s+\\d+");
    }

    private static int parseGotoPage(String line) {
        String[] parts = line.trim().split("\\s+");
        return Integer.parseInt(parts[1]);
    }

    private record SearchHit(MethodInfo method, int score) {
    }

    private record SearchState(List<MethodInfo> targets, String query, int page) {
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

    private static String enhancePrompt(String original, String error) {
        return original + """

                Previous patch failed.

                Error:
                """ + error + """

                Fix the diff so it can be applied cleanly.
                Output ONLY unified diff.
                """;
    }

    private static void writePatch(String name, String diff) throws Exception {
        try (FileWriter fw = new FileWriter(name)) {
            fw.write(diff);
        }
    }

    private static void dryRunCheck(String diff) throws Exception {
        writePatch("patch.diff", diff);
        try {
            runCommand("git", "apply", "--check", "patch.diff");
            System.out.println("Valid patch");
        } catch (Exception e) {
            System.out.println("Invalid patch");
        }
        System.out.println(diff);
    }

    private static String extractDiff(String text) {
        text = text.trim();

        if (text.startsWith("```")) {
            text = text.replaceFirst("^```.*\n", "");
            text = text.replaceFirst("\n```$", "");
        }

        int idx = text.indexOf("diff --git");
        if (idx >= 0) {
            return text.substring(idx);
        }

        idx = text.indexOf("--- ");
        if (idx >= 0) {
            return text.substring(idx);
        }

        return text;
    }

    private static void autoStash() throws Exception {
        String status = runCommandCapture("git", "status", "--porcelain");
        if (!status.isBlank()) {
            runCommand("git", "stash", "push", "-u", "-m", "ai-fix-auto");
        }
    }

    private static void ensureGitRepo() throws Exception {
        runCommand("git", "rev-parse", "--is-inside-work-tree");
    }

    private static void ensureWorkingTreeClean() throws Exception {
        String status = runCommandCapture("git", "status", "--porcelain");
        if (!status.isBlank()) {
            throw new RuntimeException("Working tree not clean. Use --stash");
        }
    }

    private static void runCommand(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd) + "\n" + out);
        }
    }

    private static String runCommandCapture(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }
}
