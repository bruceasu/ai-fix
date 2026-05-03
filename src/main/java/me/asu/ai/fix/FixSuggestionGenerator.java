package me.asu.ai.fix;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.io.CodeLoader;
import me.asu.ai.knowledge.ProblemKnowledgeRecord;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;

public class FixSuggestionGenerator {

    private static final String LABEL_MODE = "[Mode]";
    private static final String LABEL_TARGET = "[Target]";
    private static final String LABEL_UNDERSTANDING = "[Understanding]";
    private static final String LABEL_SUGGESTIONS = "[Suggestions]";
    private static final String LABEL_SAMPLE = "[Sample Code]";
    private static final String LABEL_CAUTION = "[Cautions]";
    private static final String MODE_SUGGESTION = "suggestion-only";
    private static final String TEXT_NONE = "none";
    private static final String TEXT_LOCAL_FALLBACK =
            "Local suggestion fallback mode is active. Suggestions were generated from the task description and target method code.";

    private final LLMClient llm;
    private final AppConfig config;
    private final ProjectSummary projectSummary;
    private final ProblemKnowledgeStore knowledgeStore;
    private final FixTextSupport textSupport = new FixTextSupport();

    public FixSuggestionGenerator(LLMClient llm, AppConfig config, ProjectSummary projectSummary) {
        this(llm, config, projectSummary, new ProblemKnowledgeStore(config));
    }

    public FixSuggestionGenerator(
            LLMClient llm,
            AppConfig config,
            ProjectSummary projectSummary,
            ProblemKnowledgeStore knowledgeStore) {
        this.llm = llm;
        this.config = config;
        this.projectSummary = projectSummary;
        this.knowledgeStore = knowledgeStore;
    }

    public String generate(String task, MethodInfo target) throws Exception {
        String code = CodeLoader.loadSymbol(target, config);
        try {
            String prompt = buildPrompt(task, target, code);
            String result = llm.generate(prompt);
            if (result == null || result.isBlank()) {
                return buildLocalFallback(task, target, code);
            }
            return stripMarkdownFence(result).trim();
        } catch (Exception e) {
            return buildLocalFallback(task, target, code);
        }
    }

    private String buildPrompt(String task, MethodInfo target, String code) {
        String language = safe(target.language).isBlank() ? "source code" : target.language + " code";
        String knowledgeContext = buildKnowledgeContext(task, target);
        return """
                You are a senior %s remediation advisor.

                Based on the task and target symbol below, output repair advice only.
                Do not output a diff.
                Do not output a patch.
                Do not claim that files were modified.

                Output must strictly follow this format:
                %s
                %s
                %s
                %s
                %s
                ...
                %s
                1. ...
                2. ...
                %s
                ```%s
                // If no example is appropriate, write "none"
                ```
                %s
                1. ...
                2. ...

                Constraints:
                - Suggestions must be concrete and actionable
                - Focus on the target method and only necessary nearby context
                - Avoid generic advice
                - Do not output unified diff
                - Do not claim completion

                Task:
                %s

                Project context:
                %s

                Relevant historical repair knowledge:
                %s

                Target file: %s
                Target symbol type: %s
                Target signature: %s
                Line range: %d-%d

                Current code:
                ```%s
                %s
                ```
                """.formatted(
                language,
                LABEL_MODE,
                MODE_SUGGESTION,
                LABEL_TARGET,
                textSupport.displayQualifiedTarget(target),
                LABEL_UNDERSTANDING,
                LABEL_SUGGESTIONS,
                LABEL_SAMPLE,
                fenceLanguage(target),
                LABEL_CAUTION,
                safe(task),
                buildProjectSummaryContext(target),
                knowledgeContext,
                safe(target.file),
                safe(target.symbolType),
                safe(target.signature),
                target.beginLine,
                target.endLine,
                fenceLanguage(target),
                safe(code));
    }

    private String buildKnowledgeContext(String task, MethodInfo target) {
        List<ProblemKnowledgeRecord> records = knowledgeStore.findSuccessfulRelevantRecords(task, target, 3);
        if (records.isEmpty()) {
            return TEXT_NONE;
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ProblemKnowledgeRecord record : records) {
            sb.append(index++)
                    .append(". source=")
                    .append(safe(record.source))
                    .append(", status=")
                    .append(safe(record.status))
                    .append(", classification=")
                    .append(safe(record.classification));
            String repairPattern = metadataValue(record, "repairPattern");
            if (!repairPattern.isBlank()) {
                sb.append(", pattern=").append(repairPattern);
            }
            sb.append("\n");
            if (!safe(record.target).isBlank()) {
                sb.append("   target=").append(safe(record.target)).append("\n");
            }
            if (!safe(record.analysis).isBlank()) {
                sb.append("   analysis=").append(truncateInline(record.analysis, 280)).append("\n");
            }
            if (!safe(record.repairScheme).isBlank()) {
                sb.append("   repair=").append(truncateInline(record.repairScheme, 320)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String metadataValue(ProblemKnowledgeRecord record, String key) {
        if (record == null || record.metadata == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = record.metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String buildProjectSummaryContext(MethodInfo target) {
        if (projectSummary == null) {
            return TEXT_NONE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(safe(projectSummary.projectName)).append("\n");
        sb.append("Root: ").append(safe(projectSummary.projectRoot)).append("\n");
        appendList(sb, "Packages", projectSummary.packages, 8);
        appendList(sb, "Entry points", projectSummary.entryPoints, 5);
        appendList(sb, "Integrations", projectSummary.externalIntegrations, 5);

        List<ProjectSummary.ComponentSummary> relevantComponents = findRelevantComponents(target);
        if (!relevantComponents.isEmpty()) {
            sb.append("Relevant components:\n");
            for (ProjectSummary.ComponentSummary component : relevantComponents) {
                sb.append("- ")
                        .append(component.className)
                        .append(" [")
                        .append(component.packageName)
                        .append("], methods=")
                        .append(component.methodCount)
                        .append("\n");
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

        return candidates.stream().distinct().limit(6).collect(Collectors.toList());
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
        sb.append(label)
                .append(": ")
                .append(values.stream().limit(limit).collect(Collectors.joining(", ")))
                .append("\n");
    }

    private String buildLocalFallback(String task, MethodInfo target, String code) {
        List<String> suggestions = new ArrayList<>();
        List<String> cautions = new ArrayList<>();

        if (containsNullRisk(task, code)) {
            suggestions.add("Add null checks before critical object usage and define the return or exception behavior for null inputs.");
            cautions.add("Do not change the execution order of the existing happy path when adding null guards.");
        }
        if (containsExceptionRisk(task, code)) {
            suggestions.add("Add exception handling or explicit error propagation instead of swallowing failures.");
            cautions.add("Keep existing exception types and externally observable behavior compatible.");
        }
        if (containsCollectionRisk(task, code)) {
            suggestions.add("Review collection, paging, or index boundary handling to avoid out-of-range access or empty collection misuse.");
            cautions.add("If the method returns a collection, keep the current return type and empty-collection convention consistent.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Keep the change minimal and prefer containing it within the target method.");
            suggestions.add("Review branches, return values, and dependency calls for compatibility with the requested behavior.");
        }
        if (cautions.isEmpty()) {
            cautions.add("No files were modified in this mode. Review the advice before running an automatic fix.");
            cautions.add("If the current branch has uncommitted changes, commit or organize them before attempting a real modification.");
        }

        String sampleCode = buildSampleCode(task, code);
        return """
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                1. %s
                2. %s
                %s
                ```java
                %s
                ```
                %s
                1. %s
                2. %s
                """.formatted(
                LABEL_MODE,
                MODE_SUGGESTION,
                LABEL_TARGET,
                textSupport.displayQualifiedTarget(target),
                LABEL_UNDERSTANDING,
                TEXT_LOCAL_FALLBACK,
                LABEL_SUGGESTIONS,
                suggestions.get(0),
                suggestions.size() > 1 ? suggestions.get(1) : "Validate the fix with the smallest possible change set and confirm compatibility.",
                LABEL_SAMPLE,
                sampleCode,
                LABEL_CAUTION,
                cautions.get(0),
                cautions.size() > 1 ? cautions.get(1) : "No files were modified.");
    }

    private boolean containsNullRisk(String task, String code) {
        String merged = (safe(task) + "\n" + safe(code)).toLowerCase();
        return merged.contains("null") || merged.contains("nullable");
    }

    private boolean containsExceptionRisk(String task, String code) {
        String merged = (safe(task) + "\n" + safe(code)).toLowerCase();
        return merged.contains("exception") || merged.contains("error") || merged.contains("throw");
    }

    private boolean containsCollectionRisk(String task, String code) {
        String merged = (safe(task) + "\n" + safe(code)).toLowerCase();
        return merged.contains("list")
                || merged.contains("map")
                || merged.contains("set")
                || merged.contains("collection")
                || merged.contains("index");
    }

    private String buildSampleCode(String task, String code) {
        if (containsNullRisk(task, code)) {
            return """
                    if (target == null) {
                        return;
                    }
                    """.trim();
        }
        if (containsExceptionRisk(task, code)) {
            return """
                    try {
                        invoke();
                    } catch (Exception ex) {
                        throw ex;
                    }
                    """.trim();
        }
        if (containsCollectionRisk(task, code)) {
            return """
                    if (items == null || items.isEmpty()) {
                        return List.of();
                    }
                    """.trim();
        }
        return TEXT_NONE;
    }

    private String fenceLanguage(MethodInfo target) {
        String language = safe(target.language);
        return language.isBlank() ? "text" : language;
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncateInline(String text, int max) {
        String value = safe(text).replace('\r', ' ').replace('\n', ' ').trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max)).trim() + "...";
    }
}
