package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.skill.SkillExecutionResult;
import me.asu.ai.skill.SkillOrchestrator;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatCliTest {

    @Test
    void shouldStoreAndLoadSessionSnapshot(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "sessions.dir=" + tempDir.resolve("sessions").toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        ChatCli.ChatSnapshotStore store = new ChatCli.ChatSnapshotStore(config);
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("user", "hello"));
        session.addMessage(new ChatCli.ChatMessage("assistant", "world"));

        store.store(session);
        ChatCli.ChatSession loaded = store.load("demo");

        assertEquals("demo", loaded.name());
        assertEquals(2, loaded.messages().size());
        assertEquals("hello", loaded.messages().get(0).content());
        assertEquals("world", loaded.messages().get(1).content());
        assertTrue(Files.isRegularFile(tempDir.resolve("sessions").resolve("demo.md")));
    }

    @Test
    void shouldPersistSessionSummaryInSnapshot(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "sessions.dir=" + tempDir.resolve("sessions").toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        ChatCli.ChatSnapshotStore store = new ChatCli.ChatSnapshotStore(config);
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        for (int i = 0; i < 10; i++) {
            session.addMessage(new ChatCli.ChatMessage("user", "message-" + i));
        }

        store.store(session);
        ChatCli.ChatSession loaded = store.load("demo");

        assertTrue(loaded.summary().contains("message-0"));
        assertEquals(8, loaded.messages().size());
    }

    @Test
    void shouldSummarizeOlderMessagesWhenSessionGetsLong() {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        for (int i = 0; i < 10; i++) {
            session.addMessage(new ChatCli.ChatMessage("user", "message-" + i));
        }

        assertEquals(8, session.messages().size());
        assertTrue(session.summary().contains("message-0"));
        assertTrue(session.summary().contains("message-1"));
        assertTrue(session.toTranscript().contains("session-summary:"));
        assertTrue(session.toTranscript().contains("message-9"));
    }

    @Test
    void shouldTruncateLargeMessageInTranscript() {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("assistant", "x".repeat(2000)));

        String transcript = session.toTranscript();

        assertTrue(transcript.contains("[truncated"));
        assertTrue(transcript.length() < 2100);
    }

    @Test
    void shouldCompactWebFetchActionResult() {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("assistant", """
                [action-result]
                {
                  "toolName": "web-fetch",
                  "output": "%s"
                }
                """.formatted("A".repeat(1200))));

        String transcript = session.toTranscript();

        assertTrue(transcript.contains("web-fetch excerpt"));
        assertTrue(!transcript.contains("[truncated  "));
    }

    @Test
    void shouldCompactReadFileActionResult() {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("assistant", """
                [action-result]
                {
                  "toolName": "read-file",
                  "output": "public class Demo {}",
                  "data": {
                    "path": "D:/demo.txt"
                  }
                }
                """));

        String transcript = session.toTranscript();

        assertTrue(transcript.contains("read-file snippet"));
        assertTrue(transcript.contains("path=D:/demo.txt"));
    }

    @Test
    void shouldCompactYoutubeTranscriptActionResult() {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("assistant", """
                [action-result]
                {
                  "toolName": "youtube-transcript",
                  "output": "segment text",
                  "data": {
                    "videoIds": ["abc123"]
                  }
                }
                """));

        String transcript = session.toTranscript();

        assertTrue(transcript.contains("youtube-transcript excerpt"));
        assertTrue(transcript.contains("abc123"));
    }

    @Test
    void shouldValidateGeneratedSkillYaml(@TempDir Path tempDir) throws Exception {
        Path skillFile = tempDir.resolve("skill.yaml");
        Files.writeString(
                skillFile,
                """
                        name: demo-skill
                        description: Demo
                        autoExecuteAllowed: true
                        arguments: []
                        steps:
                          - id: echo
                            type: tool
                            toolName: echo-tool
                            arguments: {}
                        """,
                StandardCharsets.UTF_8);

        String result = ChatCli.validateGeneratedSkill(skillFile);

        assertEquals("YAML parsed successfully", result);
    }

    @Test
    void shouldParseYamlActionBlock() throws Exception {
        ChatCli.ChatAction action = ChatCli.ChatActionParser.parse("""
                ```yaml
                action: tool
                name: read-file
                reason: Read a file
                expectedOutput: File content
                args:
                  path: D:/demo.txt
                ```
                """);

        assertEquals("tool", action.action());
        assertEquals("read-file", action.name());
        assertEquals("Read a file", action.reason());
        assertEquals("File content", action.expectedOutput());
        assertEquals("D:/demo.txt", action.args().get("path"));
    }

    @Test
    void shouldParseYamlActionBlockWithPlannerFields() throws Exception {
        ChatCli.ChatAction action = ChatCli.ChatActionParser.parse("""
                ```yaml
                action: skill
                name: explain-symbol
                reason: Understand one symbol before editing.
                expectedOutput: Symbol explanation and risks.
                args:
                  symbol: createOrder
                  container: OrderService
                ```
                """);

        assertEquals("skill", action.action());
        assertEquals("explain-symbol", action.name());
        assertEquals("Understand one symbol before editing.", action.reason());
        assertEquals("Symbol explanation and risks.", action.expectedOutput());
        assertEquals("createOrder", action.args().get("symbol"));
    }

    @Test
    void shouldOfferSlashCommandCompletions(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "sessions.dir=" + tempDir.resolve("sessions").toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        ChatCli.ChatCompleter completer = new ChatCli.ChatCompleter(
                new ToolCatalogService(config),
                new SkillCatalogService(config),
                new ChatCli.ChatSnapshotStore(config));

        ArrayList<Candidate> candidates = new ArrayList<>();
        completer.complete(null, new SimpleParsedLine("/"), candidates);

        assertTrue(candidates.stream().anyMatch(candidate -> "/help".equals(candidate.value())));
        assertTrue(candidates.stream().anyMatch(candidate -> "/new-skill".equals(candidate.value())));
    }

    @Test
    void followUpPromptShouldExplainCandidateHandling() {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("user", "fix createOrder"));

        String prompt = ChatCli.buildFollowUpPrompt(session, "[candidates]\n- OrderService#createOrder", 2, null);

        assertTrue(prompt.contains("[candidates]"));
        assertTrue(prompt.contains("do not guess"));
        assertTrue(prompt.contains("narrower container or file"));
    }

    @Test
    void chatRoundShouldReturnClarificationWhenActionResultContainsCandidates() throws Exception {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo", new ArrayList<>(List.of(
                new ChatCli.ChatMessage("user", "help me fix createOrder"))));

        String answer = ChatCli.chatRound(
                session,
                new SingleActionLlm(),
                new ToolCatalogService(),
                new SkillCatalogService(),
                new CandidateToolExecutor(),
                new NoOpSkillOrchestrator(),
                null,
                "groq",
                "llama-3.1-8b-instant");

        assertTrue(answer.contains("multiple candidate targets"));
        assertTrue(answer.contains("container"));
        assertTrue(answer.contains("OrderService#createOrder"));
    }

    @Test
    void handleUserTurnShouldExecuteDirectActionThenAskLlmToSummarize() throws Exception {
        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");

        String answer = ChatCli.handleUserTurn(
                session,
                """
                        请使用web-fetch工具下载网页，并分析主要内容。
                        action: tool
                        name: web-fetch
                        reason: Fetch the URL content before further processing.
                        expectedOutput: Web page content for further analysis.
                        args:
                          url: https://example.com/post
                        """,
                new FollowUpSummaryLlm(),
                new ToolCatalogService(),
                new SkillCatalogService(),
                new WebFetchToolExecutor(),
                new NoOpSkillOrchestrator(),
                null,
                "groq",
                "llama-3.1-8b-instant");

        assertTrue(answer.contains("主要内容"));
        assertTrue(session.messages().stream().anyMatch(message -> message.content().contains("[action] tool web-fetch")));
        assertTrue(session.messages().stream().anyMatch(message -> message.content().contains("Example article body")));
    }

    @Test
    void shouldInjectRepairKnowledgeIntoChatPrompt(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "app.home=" + tempDir.resolve("app-home").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);
        Path patchFile = tempDir.resolve("fix.diff");
        Files.writeString(
                patchFile,
                """
                        diff --git a/src/main/java/demo/OrderService.java b/src/main/java/demo/OrderService.java
                        --- a/src/main/java/demo/OrderService.java
                        +++ b/src/main/java/demo/OrderService.java
                        @@ -10,3 +10,6 @@
                        +if (payment == null) {
                        +    return;
                        +}
                        -run();
                        +run();
                        """,
                StandardCharsets.UTF_8);
        me.asu.ai.model.MethodInfo target = new me.asu.ai.model.MethodInfo();
        target.language = "java";
        target.file = "src/main/java/demo/OrderService.java";
        target.containerName = "demo.OrderService";
        target.symbolName = "createOrder";
        target.className = "OrderService";
        target.methodName = "createOrder";
        store.recordFixExecution("fix createOrder null handling", target, true, "", "ai-fix/test", patchFile.toString());

        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("user", "Please help me fix createOrder null bug."));

        String prompt = ChatCli.buildChatPrompt(
                session,
                List.of(),
                List.of(),
                store);

        assertTrue(prompt.contains("Relevant local repair knowledge:"));
        assertTrue(prompt.contains("status=success"));
        assertTrue(prompt.contains("demo.OrderService#createOrder"));
        assertTrue(prompt.contains("pattern=null-guard"));
    }

    @Test
    void shouldNotInjectKnowledgeForNonRepairConversation(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                "app.home=" + tempDir.resolve("app-home").toAbsolutePath().normalize().toString().replace("\\", "/"),
                StandardCharsets.UTF_8);
        AppConfig config = AppConfig.load(configFile.toString());
        ProblemKnowledgeStore store = new ProblemKnowledgeStore(config);

        ChatCli.ChatSession session = new ChatCli.ChatSession("demo");
        session.addMessage(new ChatCli.ChatMessage("user", "Explain this project structure."));

        String prompt = ChatCli.buildChatPrompt(
                session,
                List.of(),
                List.of(),
                store);

        assertTrue(prompt.contains("Relevant local repair knowledge:"));
        assertTrue(!prompt.contains("source="));
    }

    private static final class SimpleParsedLine implements ParsedLine {
        private final String line;

        private SimpleParsedLine(String line) {
            this.line = line;
        }

        @Override
        public String word() {
            return line;
        }

        @Override
        public int wordCursor() {
            return line.length();
        }

        @Override
        public int wordIndex() {
            return 0;
        }

        @Override
        public java.util.List<String> words() {
            return java.util.List.of(line);
        }

        @Override
        public String line() {
            return line;
        }

        @Override
        public int cursor() {
            return line.length();
        }
    }

    private static final class SingleActionLlm implements LLMClient {
        @Override
        public String generate(String prompt) {
            return """
                    action: tool
                    name: command-fix-suggest
                    reason: Find the target symbol first.
                    expectedOutput: Candidate symbols for narrowing.
                    args:
                      task: add null checks
                      symbol: createOrder
                    """;
        }
    }

    private static final class CandidateToolExecutor extends ToolExecutor {
        CandidateToolExecutor() {
            super(new ToolCatalogService());
        }

        @Override
        public ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed) {
            return ToolExecutionResult.success(toolName, """
                    [candidates]
                    - OrderService#createOrder
                    - LegacyOrderService#createOrder
                    """);
        }
    }

    private static final class WebFetchToolExecutor extends ToolExecutor {
        WebFetchToolExecutor() {
            super(new ToolCatalogService());
        }

        @Override
        public ToolExecutionResult execute(String toolName, String argsJson, boolean confirmed) {
            return ToolExecutionResult.success(toolName, """
                    {
                      "tool": "web-fetch",
                      "ok": true,
                      "output": "Example article body about Codex CLI and workflow design."
                    }
                    """);
        }
    }

    private static final class NoOpSkillOrchestrator extends SkillOrchestrator {
        NoOpSkillOrchestrator() {
            super(new SkillCatalogService(), new ToolExecutor(new ToolCatalogService()), AppConfig.load());
        }

        @Override
        public SkillExecutionResult execute(String skillName, String argsJson, boolean confirmed, String provider, String model) {
            return SkillExecutionResult.success(skillName, java.util.Map.of(), java.util.Map.of(), "noop");
        }
    }

    private static final class FollowUpSummaryLlm implements LLMClient {
        @Override
        public String generate(String prompt) {
            return "主要内容：这篇文章介绍了 Codex CLI 的使用模式、workflow 设计，以及如何把工具调用纳入稳定流程。";
        }
    }
}
