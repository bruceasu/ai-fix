
package me.asu.ai.patch;

import me.asu.ai.config.AppConfig;
import me.asu.ai.io.CodeLoader;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;

import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PatchRetryExecutor {
    private static final String AI_FIX_INSTRUCTIONS_FILE = "aifix.md";

    private final LLMClient llm; 
    private final String model;
    private final int maxRetry;
    private final boolean dryRun;
    private final AppConfig config;
    private final ProjectSummary projectSummary;
    private final String agentInstructions;

    public PatchRetryExecutor(
            LLMClient llm,
            String model,
            int maxRetry,
            boolean dryRun,
            AppConfig config,
            ProjectSummary projectSummary) {
        this.llm = llm;
        this.model = model;
        this.maxRetry = maxRetry;
        this.dryRun = dryRun;
        this.config = config;
        this.projectSummary = projectSummary;
        this.agentInstructions = loadAgentInstructions();
    }

    public PatchResult execute(String task, MethodInfo target) {
        try {
            String originalCode = CodeLoader.loadMethod(target, config);
            String prompt = buildInitialPrompt(task, target, originalCode);

            String lastDiff = "";
            String lastError = "";

            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                System.out.println("Attempt: " + attempt);

                String diff;

                if (attempt == 1) {
                    diff = llm.generate(prompt);
                } else {
                    String latestCode = CodeLoader.loadMethod(target, config);
                    String fixPrompt = buildFixPrompt(task, target, latestCode, lastDiff, lastError);
                    diff = llm.generate(fixPrompt);
                }

                DiffValidationResult validation = validateAndNormalizeDiff(diff);
                diff = validation.diff();

                if (!validation.valid()) {
                    lastError = validation.error();
                    continue;
                }

                String patchFile = patchFileName(target, attempt);
                writePatch(patchFile, diff);
                lastDiff = diff;

                if (dryRun) {
                    boolean valid = checkPatch(patchFile);
                    System.out.println("---- DIFF: " + patchFile + " ----");
                    System.out.println(diff);
                    return valid
                            ? PatchResult.success(target.methodName, patchFile)
                            : PatchResult.failed(target.methodName, "Patch generated but invalid in dry-run");
                }

                ApplyResult applyResult = tryApplyPatch(patchFile);

                if (applyResult.success()) {
                    return PatchResult.success(target.methodName, patchFile);
                }

                lastError = applyResult.error();
                System.out.println("Patch failed, entering debug retry if attempts remain.");
            }

            return PatchResult.failed(target.methodName, lastError);

        } catch (Exception e) {
            return PatchResult.failed(target.methodName, e.getMessage());
        }
    }



    private String buildInitialPrompt(String task, MethodInfo target, String code) {
        String template = config.getFileContent("prompt.template.file");
        if (template == null || template.isBlank()) {
            template = """
                You are a senior Java engineer.

                Task:
                {task}

                Project context summary:
                {projectSummary}

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
                Keep changes minimal and localized.
                Modify only the target method unless the task absolutely requires a nearby import or class-level change.
                Do not rename methods, classes, packages, or files unless the task explicitly requires it.
                Do not reformat unrelated code.
                Preserve existing behavior outside the requested change.
                Prefer the smallest possible patch that satisfies the task.
                """;
        }
        return prependAgentInstructions(applyTemplate(template, task, target, code, "", ""));
    }

    private String buildFixPrompt(
            String task,
            MethodInfo target,
            String latestCode,
            String failedDiff,
            String error) {
        String template = config.getFileContent("prompt.fix-template.file");
        if (template == null || template.isBlank()) {
            template = """
                You are fixing a failed git patch.

                Original task:
                {task}

                Project context summary:
                {projectSummary}

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
                Keep the correction minimal and localized.
                Do not expand the patch to unrelated methods or files.
                Prefer fixing patch context over rewriting the whole method.
                """;
        }
        return prependAgentInstructions(applyTemplate(template, task, target, latestCode, failedDiff, error));
    }

    private String applyTemplate(
            String template,
            String task,
            MethodInfo target,
            String code,
            String failedDiff,
            String error) {
        return template
                .replace("{task}", safe(task))
                .replace("{projectSummary}", safe(buildProjectSummaryContext(target)))
                .replace("{file}", safe(target.file))
                .replace("{className}", safe(target.className))
                .replace("{methodName}", safe(target.methodName))
                .replace("{signature}", safe(target.signature))
                .replace("{beginLine}", String.valueOf(target.beginLine))
                .replace("{endLine}", String.valueOf(target.endLine))
                .replace("{code}", safe(code))
                .replace("{failedDiff}", safe(failedDiff))
                .replace("{error}", safe(error));
    }

    private String prependAgentInstructions(String prompt) {
        if (agentInstructions == null || agentInstructions.isBlank()) {
            return prompt;
        }
        return """
                Base patch instructions:
                %s

                %s
                """.formatted(agentInstructions, prompt);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String buildProjectSummaryContext(MethodInfo target) {
        if (projectSummary == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(safe(projectSummary.projectName)).append("\n");
        sb.append("Project root: ").append(safe(projectSummary.projectRoot)).append("\n");
        if (!projectSummary.buildFiles.isEmpty()) {
            sb.append("Build files: ").append(String.join(", ", projectSummary.buildFiles)).append("\n");
        }
        sb.append("Stats: packages=").append(projectSummary.packageCount)
                .append(", classes=").append(projectSummary.classCount)
                .append(", methods=").append(projectSummary.methodCount)
                .append(", controllers=").append(projectSummary.controllerCount)
                .append(", services=").append(projectSummary.serviceCount)
                .append(", repositories=").append(projectSummary.repositoryCount)
                .append("\n");

        appendList(sb, "Entry points", projectSummary.entryPoints, 5);
        appendList(sb, "Packages", projectSummary.packages, 8);
        appendList(sb, "Integrations", projectSummary.externalIntegrations, 6);

        List<ProjectSummary.ComponentSummary> relevantComponents = findRelevantComponents(target);
        if (!relevantComponents.isEmpty()) {
            sb.append("Relevant components:\n");
            for (ProjectSummary.ComponentSummary component : relevantComponents) {
                sb.append("- ")
                        .append(component.className)
                        .append(" [")
                        .append(component.packageName)
                        .append("], methods=")
                        .append(component.methodCount);
                if (!component.annotations.isEmpty()) {
                    sb.append(", annotations=").append(String.join(", ", component.annotations));
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    private List<ProjectSummary.ComponentSummary> findRelevantComponents(MethodInfo target) {
        List<ProjectSummary.ComponentSummary> candidates = new ArrayList<>();
        String targetPackage = extractPackageFromFile(target.file);

        addRelevant(candidates, projectSummary.topClasses, target, targetPackage);
        addRelevant(candidates, projectSummary.controllers, target, targetPackage);
        addRelevant(candidates, projectSummary.services, target, targetPackage);
        addRelevant(candidates, projectSummary.repositories, target, targetPackage);
        addRelevant(candidates, projectSummary.configs, target, targetPackage);

        return candidates.stream()
                .distinct()
                .limit(6)
                .collect(Collectors.toList());
    }

    private void addRelevant(
            List<ProjectSummary.ComponentSummary> candidates,
            List<ProjectSummary.ComponentSummary> components,
            MethodInfo target,
            String targetPackage) {
        for (ProjectSummary.ComponentSummary component : components) {
            boolean sameClass = safe(component.className).equals(safe(target.className));
            boolean sameFile = safe(component.file).equals(safe(target.file));
            boolean samePackage = !targetPackage.isBlank() && safe(component.packageName).equals(targetPackage);
            if (sameClass || sameFile || samePackage) {
                candidates.add(component);
            }
        }
    }

    private String extractPackageFromFile(String file) {
        if (file == null || file.isBlank()) {
            return "";
        }
        String normalized = file.replace("\\", "/");
        int javaIndex = normalized.indexOf("java/");
        if (javaIndex >= 0) {
            String tail = normalized.substring(javaIndex + 5);
            int lastSlash = tail.lastIndexOf('/');
            if (lastSlash > 0) {
                return tail.substring(0, lastSlash).replace('/', '.');
            }
        }
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash > 0) {
            return normalized.substring(0, lastSlash).replace('/', '.');
        }
        return "";
    }

    private void appendList(StringBuilder sb, String label, List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sb.append(label).append(": ")
                .append(values.stream().limit(limit).collect(Collectors.joining(", ")))
                .append("\n");
    }

    private ApplyResult tryApplyPatch(String patchFile) {
        try {
            runCommand("git", "apply", "--check", patchFile);
            runCommand("git", "apply", "--3way", patchFile);
            return ApplyResult.succeeded();
        } catch (Exception e) {
            return ApplyResult.failed(e.getMessage());
        }
    }

    private boolean checkPatch(String patchFile) {
        try {
            runCommand("git", "apply", "--check", patchFile);
            System.out.println("Valid patch: " + patchFile);
            return true;
        } catch (Exception e) {
            System.out.println("Invalid patch: " + patchFile);
            System.out.println(e.getMessage());
            return false;
        }
    }

    private void writePatch(String fileName, String diff) throws Exception {
        try (FileWriter fw = new FileWriter(fileName, StandardCharsets.UTF_8)) {
            fw.write(diff);
            if (!diff.endsWith("\n")) {
                fw.write("\n");
            }
        }
    }

    private String patchFileName(MethodInfo target, int attempt) {
        return "patch_" + sanitize(target.className)
                + "_" + sanitize(target.methodName)
                + "_attempt" + attempt
                + ".diff";
    }

    private String sanitize(String value) {
        return value == null
                ? "unknown"
                : value.replaceAll("[^a-zA-Z0-9.-]", "");
    }

    private String loadAgentInstructions() {
        Path path = Paths.get(System.getProperty("user.home"), ".config", AI_FIX_INSTRUCTIONS_FILE)
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            System.err.println("Warning: failed to read agent instructions: " + path);
            return "";
        }
    }

    private DiffValidationResult validateAndNormalizeDiff(String text) {
        if (text == null) {
            return DiffValidationResult.invalid("", "Generated diff is empty");
        }

        String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return DiffValidationResult.invalid("", "Generated diff is empty");
        }

        if (trimmed.startsWith("```")) {
            String unfenced = trimmed.replaceFirst("^```(?:diff|patch)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
            if (!unfenced.equals(trimmed)) {
                trimmed = unfenced;
            }
        }

        if (startsLikeUnifiedDiff(trimmed)) {
            return DiffValidationResult.valid(ensureTrailingNewline(trimmed));
        }

        int gitDiffIndex = trimmed.indexOf("diff --git");
        int simpleDiffIndex = trimmed.indexOf("--- ");
        if (gitDiffIndex > 0 || simpleDiffIndex > 0) {
            return DiffValidationResult.invalid("", "Generated output contains non-diff text before patch");
        }

        return DiffValidationResult.invalid("", "Generated output is not a valid unified diff");
    }

    private boolean startsLikeUnifiedDiff(String text) {
        return text.startsWith("diff --git ") || text.startsWith("--- ");
    }

    private String ensureTrailingNewline(String text) {
        return text.endsWith("\n") ? text : text + "\n";
    }

    private void runCommand(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd) + "\n" + out);
        }

    }

    private record ApplyResult(boolean success, String error) {
        static ApplyResult succeeded() {
            return new ApplyResult(true, "");
        }

        static ApplyResult failed(String error) {
            return new ApplyResult(false, error);
        }

    }

    private record DiffValidationResult(boolean valid, String diff, String error) {
        static DiffValidationResult valid(String diff) {
            return new DiffValidationResult(true, diff, "");
        }

        static DiffValidationResult invalid(String diff, String error) {
            return new DiffValidationResult(false, diff, error);
        }
    }

    public record PatchResult(
            boolean success,
            String methodName,
            String patchFile,
            String error) {
        static PatchResult success(String methodName, String patchFile) {
            return new PatchResult(true, methodName, patchFile, "");
        }

        static PatchResult failed(String methodName, String error) {
            return new PatchResult(false, methodName, "", error);
        }

    }
}
