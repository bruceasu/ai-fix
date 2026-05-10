package me.asu.ai.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import me.asu.ai.chat.ActionResult;
import me.asu.ai.chat.AgentAction;
import me.asu.ai.chat.ChatCompleter;
import me.asu.ai.chat.ChatMessage;
import me.asu.ai.chat.ChatSession;
import me.asu.ai.chat.ChatSnapshotStore;
import me.asu.ai.chat.DPEFOrchestrator;
import me.asu.ai.chat.SkillGenerationResult;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.llm.PythonLLMClient;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.skill.SkillOrchestrator;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolExecutor;
import me.asu.ai.util.StringUtils;
import me.asu.ai.util.Utils;

public class ChatCli {

    private static final DateTimeFormatter SESSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_ACTION_STEPS = 4;

    private static  ChatSession currentSession ;
    private static  String currentProvider;
    private static  String currentModel;

    private static String buildPrompt(ChatSession session) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append(session.name())
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append(" > ")
                .toAnsi();
    }

    public static void main(String[] args) throws Exception {
        utf8Console();

        String configPath = Utils.findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(configPath);
        if (Utils.containsHelpFlag(args)) {
            printUsage();
            return;
        }

        currentProvider = StringUtils.valueOrDefault(Utils.findOptionValue(args, "--provider"),
                config.get("provider", AppConfig.DEFAULT_PROVIDER));
        currentModel = StringUtils.valueOrDefault(Utils.findOptionValue(args, "--model"),
                config.get("model", AppConfig.DEFAULT_MODEL));

        ToolCatalogService toolCatalog = new ToolCatalogService(config);
        SkillCatalogService skillCatalog = new SkillCatalogService(config);
        ToolExecutor toolExecutor = new ToolExecutor(toolCatalog, config);
        SkillOrchestrator orchestrator = new SkillOrchestrator(skillCatalog, toolExecutor, config);
        ProblemKnowledgeStore knowledgeStore = new ProblemKnowledgeStore(config);
        LLMClient llm = null;
        // Prefer local Python-backed LLM client by default when available.
        try {
            //TODO:  scriptPath and pythonCmd should ideally come from config or env vars, but for now we can hardcode for simplicity
                 llm = new PythonLLMClient(config.get("llm.pythonCmd", "python"),
                            Path.of(config.get("llm.scriptPath", "workspace/tools/llm/run.py")),
                            currentProvider, currentModel);
            System.out.println("Using PythonLLMClient (local script) for LLM calls.");
        } catch (Exception e) {
            System.out.println("Using provider from config: " + currentProvider);
        }

        DPEFOrchestrator dpefOrchestrator = new DPEFOrchestrator(
                toolCatalog,
                skillCatalog,
                toolExecutor,
                orchestrator,
                knowledgeStore,
                llm,
                config);

        currentSession = new ChatSession("session-" + SESSION_FORMAT.format(LocalDateTime.now()));
        ChatSnapshotStore snapshotStore = new ChatSnapshotStore(config);

        try (Terminal terminal = TerminalBuilder.builder() .system(true) .encoding(StandardCharsets.UTF_8) .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal).completer(new ChatCompleter(toolCatalog, skillCatalog, snapshotStore))
                    .variable(LineReader.HISTORY_FILE, config.getSessionsDirectory().resolve(".chat-history"))
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P   ")
                    .build();

            welcome(terminal);

            while (true) {
                String input;
                try {
                    input = readMultiLine(reader, buildPrompt(currentSession));
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
                    printInfo(terminal,
                            "Commands: /help, /store <name>, /load <name>, /new-skill <name>,/list-skills, /list-tools,  /exit",
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                    continue;
                }
                if (input.startsWith("/store ")) {
                    String name = input.substring("/store ".length()).trim();
                    snapshotStore.store(currentSession.rename(name));
                    printInfo(terminal, "Stored session: " + name,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    continue;
                }
                if (input.startsWith("/load ")) {
                    String name = input.substring("/load ".length()).trim();
                    currentSession = snapshotStore.load(name);
                    printInfo(terminal, "Loaded session: " + currentSession.name(),
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    continue;
                }
                if (input.startsWith("/new-skill ")) {
                    String name = input.substring("/new-skill ".length()).trim();
                    SkillGenerationResult generated = generateSkillFromSession(name, currentSession, llm, config);
                    printInfo(terminal, "Generated skill: " + name,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    printInfo(terminal, "Validation: " + generated.validationMessage(),
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
                    continue;
                }
                if ("/list-skills".equalsIgnoreCase(input)) {
                    String skillList = skillCatalog.all().stream()
                            .map(s -> "- " + s.name)
                            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
                    printInfo(terminal, "Available skills:\n" + skillList,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                    continue;
                }
                if ("/list-tools".equalsIgnoreCase(input)) {
                    String toolList = toolCatalog.all().stream()
                            .map(t -> "- " + t.name)
                            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
                    printInfo(terminal, "Available tools:\n" + toolList,
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                    continue;
                }

                currentSession.addMessage(new ChatMessage("user", input));
                String answer = dpefOrchestrator.runTask(currentSession, currentProvider, currentModel, new DPEFOrchestrator.Observer() {
                    @Override
                    public void onActionStarted(AgentAction action) {
                        System.out.println(new AttributedStringBuilder()
                                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                                .append(">> 执行动作: ")
                                .style(AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.CYAN))
                                .append(action.action() + ":" + action.name())
                                .toAnsi());
                    }

                    @Override
                    public void onActionFinished(AgentAction action, ActionResult result) {
                        if (result.success()) {
                            if (result.output() != null && !result.output().isBlank()) {
                                System.out.println(new AttributedStringBuilder()
                                        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                                        .append(result.output())
                                        .toAnsi());
                            }
                        } else {
                            System.err.println("!! 动作失败: " + result.error());
                        }
                    }

                    @Override
                    public void onStateChanged(DPEFOrchestrator.State oldState, DPEFOrchestrator.State newState) {
                        // Optional: show state transitions in debug mode
                    }
                });
                currentSession.addMessage(new ChatMessage("assistant", answer));
                printInfo(terminal, answer, AttributedStyle.DEFAULT);
            }
        }
    }

    private static void welcome(Terminal terminal) {
        printInfo(terminal, "Chat session started: " + currentSession.name(),
                AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
        printInfo(terminal, "Commands: /help, /exit", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
        printInfo(terminal, "Multi-line: Use '\\' at end of line to continue, or just Enter for single line.",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }

    static String stripFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:yaml|markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
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
                  /list-skills        List all available skills
                  /list-tools         List all available tools
                  /exit               Exit chat
                """);
    }

    private static String readMultiLine(LineReader reader, String prompt) {
        StringBuilder sb = new StringBuilder();
        String line = reader.readLine(prompt);
        if (line == null)
            return "";
        if (line.startsWith("/"))
            return line.trim();

        sb.append(line);

        // Loop to handle manual multi-line and open blocks
        while (shouldContinue(sb.toString())) {
            line = reader.readLine("... ");
            if (line == null || (line.isEmpty() && sb.toString().endsWith("\n")))
                break;
            sb.append("\n").append(line);
        }

        String result = sb.toString().trim();
        // Post-processing to remove manual escape characters used for multi-line
        if (result.endsWith("\\\\")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static boolean shouldContinue(String text) {
        if (text.endsWith("\\\\"))
            return true;

        // Logic to support pasting: If we detect unclosed blocks, keep reading.
        // We look for triple backticks (markdown code blocks)
        long backticks = text.codePoints().filter(ch -> ch == '`').count();
        if (backticks > 0 && (backticks / 3) % 2 != 0)
            return true;

        // Simple brace/bracket balancing can also be added here if needed
        return false;
    }

    static SkillGenerationResult generateSkillFromSession(String name, ChatSession session, LLMClient llm,
            AppConfig config) throws Exception {
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

    private static void printInfo(Terminal terminal, String text, AttributedStyle style) {
        terminal.writer().println(new AttributedStringBuilder()
                .style(style)
                .append(text)
                .toAnsi());
        terminal.flush();
    }

    private static void utf8Console() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001").inheritIO().start().waitFor();
            } catch (Exception ignored) {
            }
        }
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }

}
