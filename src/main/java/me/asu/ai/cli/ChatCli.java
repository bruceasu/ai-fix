package me.asu.ai.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeRecord;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.LLMFactory;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.skill.SkillDefinition;
import me.asu.ai.skill.SkillExecutionResult;
import me.asu.ai.skill.SkillOrchestrator;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolDefinition;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;
import me.asu.ai.util.Utils;
import me.asu.ai.model.ProjectSummary;
import me.asu.ai.util.ContextSurgerySupport;
import me.asu.ai.util.JacksonUtils;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public class ChatCli {

    private static final DateTimeFormatter SESSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_ACTION_STEPS = 4;
    private static final int RECENT_MESSAGE_LIMIT = 8;
    private static final int MAX_CONTEXT_MESSAGE_CHARS = 1600;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ProjectSummary projectSummary;

    public static void main(String[] args) throws Exception {
        fixWindowsConsole();
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        String configPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(configPath);
        if (containsHelpFlag(args)) {
            printUsage();
            return;
        }

        String provider = valueOrDefault(findOptionValue(args, "--provider"), config.get("provider", AppConfig.DEFAULT_PROVIDER));
        String model = valueOrDefault(findOptionValue(args, "--model"), config.get("model", AppConfig.DEFAULT_MODEL));

        ToolCatalogService toolCatalog = new ToolCatalogService(config);
        SkillCatalogService skillCatalog = new SkillCatalogService(config);
        ToolExecutor toolExecutor = new ToolExecutor(toolCatalog, config);
        SkillOrchestrator orchestrator = new SkillOrchestrator(skillCatalog, toolExecutor, config);
        ProblemKnowledgeStore knowledgeStore = new ProblemKnowledgeStore(config);
        LLMClient llm = LLMFactory.create(provider, model, config);
        projectSummary = loadProjectSummary(null);

        ChatSession session = new ChatSession("session-" + SESSION_FORMAT.format(LocalDateTime.now()));
        ChatSnapshotStore snapshotStore = new ChatSnapshotStore(config);

        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new ChatCompleter(toolCatalog, skillCatalog, snapshotStore))
                    .variable(LineReader.HISTORY_FILE, config.getSessionsDirectory().resolve(".chat-history"))
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P   ")
                    .build();

            printInfo(terminal, "Chat session started: " + session.name(), AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
            printInfo(terminal, "Commands: /help, /store <name>, /load <name>, /new-skill <name>, /exit", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
            printInfo(terminal, "Multi-line: Use '\\' at end of line to continue, or just Enter for single line.", AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));

            while (true) {
                String input;
                try {
                    input = readMultiLine(reader, buildPrompt(session));
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (input.isEmpty()) {
                    continue;
                }
                if ("/exit".equalsIgnoreCase(input) || "/quit".equalsIgnoreCase(input)) {
                    break;
                }
                if ("/help".equalsIgnoreCase(input)) {
                    printInfo(terminal, "Commands: /help, /store <name>, /load <name>, /new-skill <name>, /exit", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                    continue;
                }
                if (input.startsWith("/store ")) {
                    String name = input.substring("/store ".length()).trim();
                    snapshotStore.store(session.rename(name));
                    printInfo(terminal, "Stored session: " + name, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    continue;
                }
                if (input.startsWith("/load ")) {
                    String name = input.substring("/load ".length()).trim();
                    session = snapshotStore.load(name);
                    printInfo(terminal, "Loaded session: " + session.name(), AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    continue;
                }
                if (input.startsWith("/new-skill ")) {
                    String name = input.substring("/new-skill ".length()).trim();
                    SkillGenerationResult generated = generateSkillFromSession(name, session, llm, config);
                    printInfo(terminal, "Generated skill: " + name, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    printInfo(terminal, "Validation: " + generated.validationMessage(), AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
                    continue;
                }

                String answer = handleUserTurn(
                        session,
                        input,
                        llm,
                        toolCatalog,
                        skillCatalog,
                        toolExecutor,
                        orchestrator,
                        knowledgeStore,
                        provider,
                        model);
                session.addMessage(new ChatMessage("assistant", answer));
                printInfo(terminal, answer, AttributedStyle.DEFAULT);
            }
        }
    }

    static String handleUserTurn(
            ChatSession session,
            String input,
            LLMClient llm,
            ToolCatalogService toolCatalog,
            SkillCatalogService skillCatalog,
            ToolExecutor toolExecutor,
            SkillOrchestrator orchestrator,
            ProblemKnowledgeStore knowledgeStore,
            String provider,
            String model) throws Exception {
        session.addMessage(new ChatMessage("user", input));
        ChatAction directAction = tryParseUserAction(input);
        if (directAction != null) {
            String latestResult = executeAction(directAction, toolExecutor, orchestrator, provider, model);
            session.addMessage(new ChatMessage("assistant", "[action] " + directAction.action() + " " + directAction.name()));
            session.addMessage(new ChatMessage("assistant", "[action-result]\n" + latestResult));
            if (containsCandidates(latestResult)) {
                return buildCandidateClarificationPlain(latestResult);
            }
            return continueAfterAction(session, latestResult, llm, toolExecutor, orchestrator, knowledgeStore, provider, model, 2);
        }
        return chatRound(session, llm, toolCatalog, skillCatalog, toolExecutor, orchestrator, knowledgeStore, provider, model);
    }

    static String chatRound(
            ChatSession session,
            LLMClient llm,
            ToolCatalogService toolCatalog,
            SkillCatalogService skillCatalog,
            ToolExecutor toolExecutor,
            SkillOrchestrator orchestrator,
            ProblemKnowledgeStore knowledgeStore,
            String provider,
            String model) throws Exception {
        String latestResult = "";
        for (int step = 0; step < MAX_ACTION_STEPS; step++) {
            String prompt = step == 0
                    ? buildChatPrompt(session, toolCatalog.all(), skillCatalog.all(), knowledgeStore)
                    : buildFollowUpPrompt(session, latestResult, step + 1, knowledgeStore);

            ChatAction action = tryStructuredAction(llm, prompt);
            if (action == null) {
                return stripFence(llm.generateText(prompt));
            }

            latestResult = executeAction(action, toolExecutor, orchestrator, provider, model);
            session.addMessage(new ChatMessage("assistant", "[action] " + action.action() + " " + action.name()));
            session.addMessage(new ChatMessage("assistant", "[action-result]\n" + latestResult));
            if (containsCandidates(latestResult)) {
                return buildCandidateClarificationPlain(latestResult);
            }
        }
        return "Action loop stopped after " + MAX_ACTION_STEPS + " steps. Please narrow the request.";
    }

    private static String continueAfterAction(
            ChatSession session,
            String latestResult,
            LLMClient llm,
            ToolExecutor toolExecutor,
            SkillOrchestrator orchestrator,
            ProblemKnowledgeStore knowledgeStore,
            String provider,
            String model,
            int nextStep) throws Exception {
        String currentResult = latestResult;
        for (int step = nextStep; step <= MAX_ACTION_STEPS; step++) {
            String prompt = buildFollowUpPrompt(session, currentResult, step, knowledgeStore);
            ChatAction nextAction = tryStructuredAction(llm, prompt);
            if (nextAction == null) {
                return stripFence(llm.generateText(prompt));
            }

            currentResult = executeAction(nextAction, toolExecutor, orchestrator, provider, model);
            session.addMessage(new ChatMessage("assistant", "[action] " + nextAction.action() + " " + nextAction.name()));
            session.addMessage(new ChatMessage("assistant", "[action-result]\n" + currentResult));
            if (containsCandidates(currentResult)) {
                return buildCandidateClarificationPlain(currentResult);
            }
        }
        return "Action loop stopped after " + MAX_ACTION_STEPS + " steps. Please narrow the request.";
    }

    private static ChatAction tryStructuredAction(LLMClient llm, String prompt) {
        try {
            return llm.generateYaml(prompt, ChatAction.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ChatAction tryParseUserAction(String input) {
        try {
            ChatAction action = ChatActionParser.parse(input);
            if (action == null || action.action().isBlank() || action.name().isBlank()) {
                return null;
            }
            return action;
        } catch (Exception ignored) {
            return null;
        }
    }

    static SkillGenerationResult generateSkillFromSession(String name, ChatSession session, LLMClient llm, AppConfig config) throws Exception {
        String prompt = """
                You are an ai-fix skill author.
                Generate one new ai-fix skill definition from the following conversation.

                Requirements:
                - Output YAML only
                - The YAML must be compatible with the current ai-fix skill format
                - Use "%s" as the skill name
                - Reuse existing tools or skills when possible
                - If the workflow is uncertain, still provide a minimal usable version

                Conversation:
                %s
                """.formatted(name, session.toTranscript());
        String yaml = stripFence(llm.generateText(prompt));
        Path skillDir = config.getSkillsDirectory().resolve(name).normalize();
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("skill.yaml");
        Files.writeString(skillFile, yaml, StandardCharsets.UTF_8);
        String validation = validateGeneratedSkill(skillFile);
        return new SkillGenerationResult(skillFile, validation);
    }

    static String validateGeneratedSkill(Path skillFile) throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.readValue(skillFile.toFile(), new TypeReference<Map<String, Object>>() {
        });
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        if (!content.contains("name:")) {
            throw new IllegalStateException("Generated skill is missing name.");
        }
        if (!content.contains("steps:")) {
            throw new IllegalStateException("Generated skill is missing steps.");
        }
        return "YAML parsed successfully";
    }

    private static String executeAction(
            ChatAction action,
            ToolExecutor toolExecutor,
            SkillOrchestrator orchestrator,
            String provider,
            String model) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        if ("tool".equalsIgnoreCase(action.action())) {
            ToolExecutionResult result = toolExecutor.execute(
                    action.name(),
                    mapper.writeValueAsString(action.args()),
                    true);
            return result.ok()
                    ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.toMap())
                    : "Tool failed: " + result.error();
        }
        if ("skill".equalsIgnoreCase(action.action())) {
            SkillExecutionResult result = orchestrator.execute(
                    action.name(),
                    mapper.writeValueAsString(action.args()),
                    true,
                    provider,
                    model);
            return result.ok()
                    ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.toMap())
                    : "Skill failed: " + result.error();
        }
        return "Unsupported action: " + action.action();
    }

    static String buildChatPrompt(
            ChatSession session,
            List<ToolDefinition> tools,
            List<SkillDefinition> skills,
            ProblemKnowledgeStore knowledgeStore) {
        String knowledgeContext = buildRepairKnowledgeContext(session, knowledgeStore);
        String projectContext = "";
        if (projectSummary != null) {
            String lastUserMsg = session.messages().stream()
                    .filter(m -> m.role.equals("user"))
                    .reduce((first, second) -> second)
                    .map(m -> m.content)
                    .orElse("");
            ProjectSummary slimmed = ContextSurgerySupport.performSurgery(projectSummary, lastUserMsg);
            try {
                projectContext = "Project Context (filtered blueprint):\n" + 
                        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(slimmed);
            } catch (Exception e) {
                projectContext = "Project summary available but failed to filter.";
            }
        }

        return """
                You are the interactive ai-fix developer assistant.
                You can:
                1. Answer directly
                2. Request one tool execution
                3. Request one skill execution

                If you need a tool or skill, output exactly one YAML block like this:
                ```yaml
                action: tool
                name: read-file
                reason: Read the source file before answering.
                expectedOutput: File content for analysis.
                args:
                  path: D:/example.txt
                ```

                Or:
                ```yaml
                action: skill
                name: explain-symbol
                reason: The user is asking about one specific symbol.
                expectedOutput: A focused explanation of the symbol and its risks.
                args:
                  symbol: createOrder
                  container: OrderService
                ```

                Rules:
                - Request at most one action per turn
                - If no action is needed, return the final answer directly in Chinese
                - Prefer existing tools and skills
                - Do not invent tool or skill names
                - Keep reason and expectedOutput concise and actionable
                - For Java error analysis, prefer analyze-java-error
                - For test failure analysis, prefer analyze-test-failure
                - For project structure explanation, prefer explain-project-structure or find-entry-points
                - For symbol explanation, prefer explain-symbol
                - For method optimization review, prefer review-method-optimization
                - For code review of current changes, prefer review-changed-files
                - Core commands such as index, analyze, understand, and fix are stable CLI entry points; use the closest built-in skill only when working inside chat
                - If the user explicitly wants to run a core command workflow inside chat, prefer these bridge tools:
                  command-analyze, command-index, command-understand, command-fix-suggest

                Available tools:
                %s

                Available skills:
                %s

                Relevant local repair knowledge:
                %s

                Project Context:
                %s

                Conversation:
                %s
                """.formatted(
                tools.stream().map(tool -> "- " + tool.name).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b),
                skills.stream().map(skill -> "- " + skill.name).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b),
                knowledgeContext,
                projectContext,
                session.toTranscript());
    }

    static String buildFollowUpPrompt(
            ChatSession session,
            String actionResult,
            int step,
            ProblemKnowledgeStore knowledgeStore) {
        String knowledgeContext = buildRepairKnowledgeContext(session, knowledgeStore);
        String projectContext = "";
        if (projectSummary != null) {
            String lastUserMsg = session.messages().stream()
                    .filter(m -> m.role.equals("user"))
                    .reduce((first, second) -> second)
                    .map(m -> m.content)
                    .orElse("");
            ProjectSummary slimmed = ContextSurgerySupport.performSurgery(projectSummary, lastUserMsg);
            try {
                projectContext = "Project Context (filtered blueprint):\n" + 
                        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(slimmed);
            } catch (Exception e) {
                projectContext = "Project summary available but failed to filter.";
            }
        }
        return """
                You already have one tool or skill result.
                Continue based on the conversation and the latest result.
                This is step %d of at most %d.

                If another step is still needed, output exactly one YAML action block.
                The YAML action should still contain action, name, reason, expectedOutput, and args.
                If the latest result contains [candidates], do not guess. Ask for a narrower container or file by returning one follow-up action only after the user gives more target details.
                If you already have enough information, answer directly in Chinese and do not output another action.

                Project Context:
                %s

                Conversation:
                %s

                Relevant local repair knowledge:
                %s

                Latest action result:
                %s
                """.formatted(
                step,
                MAX_ACTION_STEPS,
                projectContext,
                session.toTranscript(),
                knowledgeContext,
                actionResult);
    }


    static boolean containsCandidates(String actionResult) {
        return actionResult != null && actionResult.contains("[candidates]");
    }

    static String buildCandidateClarification(String actionResult) {
        String normalized = actionResult == null ? "" : actionResult.replace("\\n", "\n");
        List<String> candidates = normalized.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("- "))
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("检测到多个候选目标，我先不继续猜测。\n");
        sb.append("请补充更精确的 `container` 或 `file`，然后我再继续执行下一步。");
        if (!candidates.isEmpty()) {
            sb.append("\n\n候选目标：\n");
            candidates.forEach(line -> sb.append(line).append("\n"));
        }
        return sb.toString().trim();
    }

    /*
    static String buildCandidateClarificationSafe(String actionResult) {
        String normalized = actionResult == null ? "" : actionResult.replace("\\n", "\n");
        List<String> candidates = normalized.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("- "))
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("检测到多个候选目标，我先不继续猜测。");
        sb.append("\n请补充更精确的 `container` 或 `file`，然后我再继续执行下一步。");
        if (!candidates.isEmpty()) {
            sb.append("\n\n候选目标：\n");
            candidates.forEach(line -> sb.append(line).append("\n"));
        }
        return sb.toString().trim();
    }

    */
    static String buildCandidateClarificationPlain(String actionResult) {
        String normalized = actionResult == null ? "" : actionResult.replace("\\n", "\n");
        List<String> candidates = normalized.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("- "))
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("Found multiple candidate targets. I will not guess.\n");
        sb.append("Please provide a narrower `container` or `file`, then I can continue.\n");
        if (!candidates.isEmpty()) {
            sb.append("\nCandidates:\n");
            candidates.forEach(line -> sb.append(line).append("\n"));
        }
        return sb.toString().trim();
    }

    private static String buildRepairKnowledgeContext(ChatSession session, ProblemKnowledgeStore knowledgeStore) {
        if (knowledgeStore == null || session == null) {
            return "none";
        }
        String query = latestRepairRelevantUserInput(session);
        if (query.isBlank()) {
            return "none";
        }
        List<ProblemKnowledgeRecord> records = knowledgeStore.searchSuccessfulRecords(query, 3);
        if (records.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ProblemKnowledgeRecord record : records) {
            sb.append(index++)
                    .append(". source=")
                    .append(safe(record.source))
                    .append(", status=")
                    .append(safe(record.status))
                    .append(", type=")
                    .append(firstNonBlank(record.classification, record.problemType, "unknown"));
            String repairPattern = metadataValue(record, "repairPattern");
            if (!repairPattern.isBlank()) {
                sb.append(", pattern=").append(repairPattern);
            }
            sb.append("\n");
            if (!safe(record.target).isBlank()) {
                sb.append("   target=").append(safe(record.target)).append("\n");
            }
            if (!safe(record.repairScheme).isBlank()) {
                sb.append("   repair=").append(shortenKnowledge(record.repairScheme, 260)).append("\n");
            }
            if (!safe(record.analysis).isBlank()) {
                sb.append("   analysis=").append(shortenKnowledge(record.analysis, 220)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String latestRepairRelevantUserInput(ChatSession session) {
        for (int i = session.messages().size() - 1; i >= 0; i--) {
            ChatMessage message = session.messages().get(i);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            String content = safe(message.content());
            if (looksRepairRelated(content)) {
                return content;
            }
        }
        return "";
    }

    private static boolean looksRepairRelated(String text) {
        String normalized = safe(text).toLowerCase();
        return normalized.contains("fix")
                || normalized.contains("bug")
                || normalized.contains("error")
                || normalized.contains("exception")
                || normalized.contains("patch")
                || normalized.contains("failure")
                || normalized.contains("null")
                || normalized.contains("修复")
                || normalized.contains("错误")
                || normalized.contains("异常")
                || normalized.contains("失败");
    }

    private static String metadataValue(ProblemKnowledgeRecord record, String key) {
        if (record == null || record.metadata == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = record.metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String buildPrompt(ChatSession session) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append(session.name())
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append(" > ")
                .toAnsi();
    }

    private static void printInfo(Terminal terminal, String text, AttributedStyle style) {
        terminal.writer().println(new AttributedStringBuilder()
                .style(style)
                .append(text)
                .toAnsi());
        terminal.flush();
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

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String stripFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:yaml|markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private static String sanitizeMessageContent(String text) {
        return compactMessage(text).replace("\r", "");
    }

    private static String compactMessage(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r", "").trim();
        String specialized = compactToolResult(normalized);
        if (specialized != null) {
            return specialized;
        }
        if (normalized.length() <= MAX_CONTEXT_MESSAGE_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTEXT_MESSAGE_CHARS)
                + "\n...[truncated " + (normalized.length() - MAX_CONTEXT_MESSAGE_CHARS) + " chars]";
    }

    private static String compactToolResult(String text) {
        if (!text.startsWith("[action-result]")) {
            return null;
        }
        String payload = text.substring("[action-result]".length()).trim();
        try {
            Map<String, Object> root = JSON.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
            Object toolName = root.get("toolName");
            Object output = root.get("output");
            if ("web-fetch".equals(toolName)) {
                return summarizeWebFetch(output);
            }
            if ("read-file".equals(toolName)) {
                return summarizeReadFile(root, output);
            }
            if ("youtube-transcript".equals(toolName)) {
                return summarizeYoutubeTranscript(root, output);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String summarizeWebFetch(Object output) {
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        if (content.isBlank()) {
            return "[action-result]\nweb-fetch: empty content";
        }
        String excerpt = content.length() <= 600 ? content : content.substring(0, 600) + "...";
        return "[action-result]\nweb-fetch excerpt:\n" + excerpt;
    }

    @SuppressWarnings("unchecked")
    private static String summarizeReadFile(Map<String, Object> root, Object output) {
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String path = String.valueOf(data.getOrDefault("path", "unknown"));
        String excerpt = content.length() <= 600 ? content : content.substring(0, 600) + "...";
        return "[action-result]\nread-file snippet:\npath=" + path + "\n" + excerpt;
    }

    @SuppressWarnings("unchecked")
    private static String summarizeYoutubeTranscript(Map<String, Object> root, Object output) {
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Object videoIds = data.get("videoIds");
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        String excerpt = content.length() <= 700 ? content : content.substring(0, 700) + "...";
        return "[action-result]\nyoutube-transcript excerpt:\nvideoIds=" + videoIds + "\n" + excerpt;
    }

    private static String trimSummary(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return "...[older summary truncated]\n" + text.substring(text.length() - maxChars);
    }

    private static String shortenKnowledge(String text, int maxChars) {
        String value = safe(text).replace('\r', ' ').replace('\n', ' ').trim();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars)).trim() + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar chat [--provider <provider>] [--model <model>] [--config <path>]

                Commands inside chat:
                  /store <name>       Save session snapshot as Markdown
                  /load <name>        Load session snapshot from Markdown
                  /new-skill <name>   Generate a new skill from this session
                  /help               Show help
                  /exit               Exit chat
                """);
    }

    record ChatMessage(String role, String content) {
    }

    static final class ChatSession {
        private final String name;
        private final List<ChatMessage> messages;
        private String summary;

        ChatSession(String name) {
            this(name, new ArrayList<>(), "");
        }

        ChatSession(String name, List<ChatMessage> messages) {
            this(name, messages, "");
        }

        ChatSession(String name, List<ChatMessage> messages, String summary) {
            this.name = name;
            this.messages = messages;
            this.summary = summary == null ? "" : summary;
        }

        String name() {
            return name;
        }

        List<ChatMessage> messages() {
            return messages;
        }

        String summary() {
            return summary;
        }

        void addMessage(ChatMessage message) {
            messages.add(message);
            summarizeIfNeeded();
        }

        ChatSession rename(String newName) {
            return new ChatSession(newName, new ArrayList<>(messages), summary);
        }

        String toTranscript() {
            StringBuilder sb = new StringBuilder();
            if (!summary.isBlank()) {
                sb.append("session-summary: ").append(summary).append("\n\n");
            }
            for (ChatMessage message : messages) {
                sb.append(message.role()).append(": ").append(sanitizeMessageContent(message.content())).append("\n\n");
            }
            return sb.toString().trim();
        }

        private void summarizeIfNeeded() {
            while (messages.size() > RECENT_MESSAGE_LIMIT) {
                ChatMessage removed = messages.remove(0);
                String condensed = removed.role() + ": " + compactMessage(removed.content());
                if (summary.isBlank()) {
                    summary = condensed;
                } else {
                    summary = summary + "\n" + condensed;
                }
                summary = trimSummary(summary, 4000);
            }
        }
    }

    static final class ChatSnapshotStore {
        private final AppConfig config;

        ChatSnapshotStore(AppConfig config) {
            this.config = config;
        }

        void store(ChatSession session) throws Exception {
            Path dir = config.getSessionsDirectory();
            Files.createDirectories(dir);
            Path file = dir.resolve(session.name() + ".md");
            StringBuilder sb = new StringBuilder();
            sb.append("# ai-fix chat session\n\n");
            sb.append("- name: ").append(session.name()).append("\n\n");
            if (!session.summary().isBlank()) {
                sb.append("## session-summary\n\n");
                sb.append(session.summary()).append("\n\n");
            }
            for (ChatMessage message : session.messages()) {
                sb.append("## ").append(message.role()).append("\n\n");
                sb.append(message.content()).append("\n\n");
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        }

        ChatSession load(String name) throws Exception {
            Path file = config.getSessionsDirectory().resolve(name + ".md").normalize();
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<ChatMessage> messages = new ArrayList<>();
            String role = null;
            StringBuilder body = new StringBuilder();
            String summary = "";
            for (String line : lines) {
                if (line.startsWith("## ")) {
                    if (role != null) {
                        if ("session-summary".equals(role)) {
                            summary = body.toString().trim();
                        } else {
                            messages.add(new ChatMessage(role, body.toString().trim()));
                        }
                    }
                    role = line.substring(3).trim();
                    body = new StringBuilder();
                    continue;
                }
                if (role != null) {
                    body.append(line).append("\n");
                }
            }
            if (role != null) {
                if ("session-summary".equals(role)) {
                    summary = body.toString().trim();
                } else {
                    messages.add(new ChatMessage(role, body.toString().trim()));
                }
            }
            return new ChatSession(name, messages, summary);
        }

        List<String> listSessionNames() {
            try {
                Path dir = config.getSessionsDirectory();
                if (!Files.isDirectory(dir)) {
                    return List.of();
                }
                return Files.list(dir)
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".md"))
                        .map(name -> name.substring(0, name.length() - 3))
                        .sorted()
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
    }

    record ChatAction(String action, String name, String reason, String expectedOutput, Map<String, Object> args) {
        ChatAction {
            if (action == null) {
                action = "";
            }
            if (name == null) {
                name = "";
            }
            if (reason == null) {
                reason = "";
            }
            if (expectedOutput == null) {
                expectedOutput = "";
            }
            if (args == null) {
                args = new LinkedHashMap<>();
            }
        }
    }

    record SkillGenerationResult(Path skillFile, String validationMessage) {
    }

    static final class ChatActionParser {
        private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

        static ChatAction parse(String text) {
            if (text == null || text.isBlank()) return null;
            
            try {
                String yaml = extractYaml(text);
                if (yaml == null) return null;
                
                ChatAction action = YAML.readValue(yaml, ChatAction.class);
                if (action != null && action.action() != null && !action.action().isBlank()) {
                    return action;
                }
            } catch (Exception e) {
                // Silently ignore parsing errors for conversational text
            }
            return null;
        }

        private static String extractYaml(String text) {
            // Find ```yaml ... ```
            int start = text.indexOf("```yaml");
            if (start >= 0) {
                int end = text.indexOf("```", start + 7);
                if (end > start) {
                    return text.substring(start + 7, end).trim();
                }
            }
            
            // Fallback: look for "action:" if not in a fence
            int actionIndex = text.indexOf("action:");
            if (actionIndex >= 0) {
                // Find end of YAML block or end of text
                int nextFence = text.indexOf("```", actionIndex);
                if (nextFence > actionIndex) {
                    return text.substring(actionIndex, nextFence).trim();
                }
                return text.substring(actionIndex).trim();
            }
            return null;
        }
    }

    static final class ChatCompleter implements Completer {
        private final ToolCatalogService toolCatalog;
        private final SkillCatalogService skillCatalog;
        private final ChatSnapshotStore snapshotStore;

        ChatCompleter(
                ToolCatalogService toolCatalog,
                SkillCatalogService skillCatalog,
                ChatSnapshotStore snapshotStore) {
            this.toolCatalog = toolCatalog;
            this.skillCatalog = skillCatalog;
            this.snapshotStore = snapshotStore;
        }

        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word().trim();
            String full = line.line().trim();

            if (full.startsWith("/store ")) {
                candidates.add(new Candidate("demo-session"));
                return;
            }
            if (full.startsWith("/load ")) {
                snapshotStore.listSessionNames().forEach(name -> candidates.add(new Candidate(name)));
                return;
            }
            if (full.startsWith("/new-skill ")) {
                candidates.add(new Candidate("my-new-skill"));
                return;
            }
            if (word.startsWith("/")) {
                List.of("/help", "/store", "/load", "/new-skill", "/exit")
                        .forEach(command -> candidates.add(new Candidate(command)));
                return;
            }

            toolCatalog.all().stream()
                    .map(tool -> "tool:" + tool.name)
                    .forEach(value -> candidates.add(new Candidate(value)));
            skillCatalog.all().stream()
                    .map(skill -> "skill:" + skill.name)
                    .forEach(value -> candidates.add(new Candidate(value)));
        }
    }

    private static String readMultiLine(LineReader reader, String prompt) {
        StringBuilder sb = new StringBuilder();
        String line = reader.readLine(prompt);
        if (line == null) return "";
        if (line.startsWith("/")) return line.trim();

        sb.append(line);
        while (line.endsWith("\\\\")) {
            sb.setLength(sb.length() - 1);
            sb.append("\n");
            line = reader.readLine(">   ");
            if (line == null) break;
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private static void fixWindowsConsole() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001").inheritIO().start().waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    private static ProjectSummary loadProjectSummary(String projectSummaryPath) {
        Path path;
        if (projectSummaryPath == null || projectSummaryPath.isBlank()) {
            path = Utils.findFileUpwards("project-summary.json");
        } else {
            path = Paths.get(projectSummaryPath).toAbsolutePath().normalize();
        }

        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return JacksonUtils.deserialize(path.toFile(), ProjectSummary.class);
        } catch (Exception e) {
            return null;
        }
    }
}
