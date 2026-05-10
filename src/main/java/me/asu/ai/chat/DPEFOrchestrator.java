package me.asu.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.ai.config.AppConfig;
import me.asu.ai.knowledge.ProblemKnowledgeRecord;
import me.asu.ai.knowledge.ProblemKnowledgeStore;
import me.asu.ai.llm.LLMClient;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.skill.SkillExecutionResult;
import me.asu.ai.skill.SkillOrchestrator;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;
import me.asu.ai.tool.PythonToolDispatcherSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The DPEF Orchestrator (Harness) that manages the Agent lifecycle.
 */
public class DPEFOrchestrator {

    private static final int MAX_ACTION_STEPS = 10;
    
    public enum State {
        PLANNING,  // Decompose & Checklist
        EXECUTE,   // Run tool/skill
        VERIFY,    // Check results
        COMPLETE   // Task finished
    }

    private final ToolCatalogService toolCatalog;
    private final SkillCatalogService skillCatalog;
    private final SkillOrchestrator skillOrchestrator;
    private final LLMClient llm;
    private final ProblemKnowledgeStore knowledgeStore;
    private final AppConfig config;
    private final PythonToolDispatcherSupport pythonDispatcher;
    private final ObjectMapper mapper = new ObjectMapper();

    private List<String> checklist = new ArrayList<>();
    private Set<String> actionHistory = new HashSet<>();

    public DPEFOrchestrator(
            ToolCatalogService toolCatalog,
            SkillCatalogService skillCatalog,
            ToolExecutor toolExecutor,
            SkillOrchestrator orchestrator,
            ProblemKnowledgeStore knowledgeStore,
            LLMClient llm,
            AppConfig config) {
        this.toolCatalog = toolCatalog;
        this.skillCatalog = skillCatalog;
        this.knowledgeStore = knowledgeStore;
        this.llm = llm;
        this.config = config;
        this.skillOrchestrator = orchestrator != null ? orchestrator : new SkillOrchestrator(skillCatalog, toolExecutor, config);
        this.pythonDispatcher = new PythonToolDispatcherSupport(config);
    }

    public String runTask(ChatSession session, String provider, String model, Observer observer) throws Exception {
        State state = State.PLANNING;
        String latestFeedback = "";
        checklist.clear();
        actionHistory.clear();
        
        for (int step = 1; step <= MAX_ACTION_STEPS; step++) {
            String prompt = buildPromptForState(state, session, latestFeedback, step);
            String response = llm.generateText(prompt);
            
            if (state == State.PLANNING) {
                updateChecklist(response);
                if (!checklist.isEmpty()) {
                    if (observer != null) observer.onPlanUpdated(checklist);
                    state = State.EXECUTE;
                    continue; 
                } else {
                    return stripFence(response);
                }
            }

            AgentAction action = tryParseAction(response);
            if (action == null) {
                return stripFence(response);
            }

            String actionKey = action.name() + ":" + action.args().toString();
            if (actionHistory.contains(actionKey)) {
                latestFeedback = "REPETITION ERROR: You are repeating an action that already failed or gave feedback. " +
                                 "Check Capabilities [args: ...] for correct parameter names (snake_case). " +
                                 "If a Skill failed, try using individual Tools instead.";
                continue;
            }
            actionHistory.add(actionKey);

            if (observer != null) observer.onActionStarted(action);
            ActionResult result = executeAction(action, provider, model);
            if (observer != null) observer.onActionFinished(action, result);

            session.addMessage(new ChatMessage("assistant", "[action] " + action.action() + " " + action.name()));
            String feedback = result.success() ? result.output() : "Error: " + result.error();
            if (result.hints() != null) {
                feedback += "\n[Hint: " + result.hints() + "]";
            }
            session.addMessage(new ChatMessage("assistant", "[action-result]\n" + feedback));
            
            latestFeedback = feedback;
            state = State.EXECUTE;
        }
        
        String finalPrompt = buildPromptForState(State.VERIFY, session, latestFeedback, MAX_ACTION_STEPS);
        return stripFence(llm.generateText(finalPrompt));
    }

    private void updateChecklist(String response) {
        checklist.clear();
        response.lines()
            .map(String::trim)
            .filter(l -> l.startsWith("- [") || l.startsWith("-") || l.startsWith("*") || (l.length() > 2 && Character.isDigit(l.charAt(0))))
            .forEach(checklist::add);
    }

    public ActionResult executeAction(AgentAction action, String provider, String model) {
        try {
            String argsJson = mapper.writeValueAsString(action.args());
            String actionType = normalizeActionType(action.action(), action.name());
            
            ToolExecutionResult res;
            if ("skill".equalsIgnoreCase(actionType)) {
                SkillExecutionResult skillRes = skillOrchestrator.execute(action.name(), argsJson, true, provider, model);
                if (skillRes != null && skillRes.ok()) {
                    res = ToolExecutionResult.success(action.name(), skillRes.summary(), skillRes.toMap());
                } else if (skillRes != null) {
                    res = ToolExecutionResult.failure(action.name(), skillRes.error());
                } else {
                    res = ToolExecutionResult.failure(action.name(), "Skill execution unavailable");
                }
            } else {
                res = pythonDispatcher.execute(
                        action.name(),
                        argsJson,
                        true,
                        provider,
                        model
                );
            }

            String output = res.ok() ? res.output() : null;
            Map<String, Object> data = res.ok() ? res.data() : Map.of();
            String error = res.ok() ? null : res.error();
            String hints = generateHints(action, res);

            if (res.ok()) {
                return ActionResult.success(output, data, hints);
            } else {
                return new ActionResult(false, null, data, error, hints);
            }
        } catch (Exception e) {
            return ActionResult.failure("Execution system error: " + e.getMessage());
        }
    }


    public interface Observer {
        void onActionStarted(AgentAction action);
        void onActionFinished(AgentAction action, ActionResult result);
        void onStateChanged(State oldState, State newState);
        default void onPlanUpdated(List<String> checklist) {}
    }

    private String normalizeActionType(String type, String name) {
        String t = type == null ? "" : type.toLowerCase().trim();
        String n = name == null ? "" : name.trim();
        if (skillCatalog.all().stream().anyMatch(s -> s.name.equals(n))) return "skill";
        if (skillCatalog.all().stream().anyMatch(s -> s.name.equals(t))) return "skill";
        if (toolCatalog.all().stream().anyMatch(tool -> tool.name.equals(n) || tool.name.equals(t))) return "tool";
        return "tool";
    }

    private String generateHints(AgentAction action, ToolExecutionResult res) {
        if (res.ok()) return null;

        String error = res.error() == null ? "" : res.error().toLowerCase();
        if (error.contains("missing") || error.contains("unrecognized arguments")) {
            return "PARAMETER ERROR: Use EXACT argument names from Capabilities [args: ...]. Check for snake_case.";
        }
        if (error.contains("not found")) {
            return "PATH ERROR: File/Directory not found. Use 'list-files' to verify paths.";
        }
        return "Action failed. Try a different approach or verify parameters.";
    }

    private String buildPromptForState(State state, ChatSession session, String feedback, int step) {
        return switch (state) {
            case PLANNING -> buildPlanningPrompt(session);
            case EXECUTE -> buildExecutePrompt(session, feedback, step);
            case VERIFY -> buildVerifyPrompt(session, feedback);
            case COMPLETE -> "";
        };
    }

    private String buildPlanningPrompt(ChatSession session) {
        return """
                # Phase: PLANNING
                You are an AUTONOMOUS Agent. Analyze the goal and output a Checklist.
                
                # IMPORTANT RULES:
                1. USE SKILLS FIRST: If a Skill under "PROVEN WORKFLOWS" matches the goal, use it as step 1. 
                   Skills are high-quality, pre-tested combinations of tools.
                2. Use exact IDs for actions.
                3. Format checklist with `- [ ]`.
                
                # PROVEN WORKFLOWS (Preferred Skills)
                %s
                
                # ATOMIC TOOLS
                %s
                
                # History
                %s
                """.formatted(getSkillsSummary(), getToolsSummary(), session.toTranscript());
    }

    private String buildExecutePrompt(ChatSession session, String feedback, int step) {
        String os = System.getProperty("os.name");
        String planStr = String.join("\n", checklist);
        return """
                # System Info
                OS: %s
                Step: %d/%d
                
                # Current Plan
                %s
                
                # Latest Feedback (ACTUAL DATA)
                %s

                # Task
                1. If goal achieved, answer in Chinese (NO YAML).
                2. If not, output ONLY ONE YAML block for the next action.
                
                # Rules:
                - Use EXACT argument keys (args: ...). Use snake_case.
                - Use FORWARD SLASHES `/` for paths.
                
                # Available Capabilities
                %s
                
                # History
                %s
                """.formatted(os, step, MAX_ACTION_STEPS, planStr, feedback, getCapabilitiesSummary(), session.toTranscript());
    }

    private String buildVerifyPrompt(ChatSession session, String feedback) {
        return """
                # Phase: VERIFY
                The process has finished. Provide a final summary in Chinese.
                
                Latest Feedback:
                %s
                
                # History
                %s
                """.formatted(feedback, session.toTranscript());
    }

    private String getSkillsSummary() {
        StringBuilder sb = new StringBuilder();
        skillCatalog.all().forEach(s -> {
            sb.append("- ").append(s.name).append(": ").append(safe(s.description));
            if (s.arguments != null && !s.arguments.isEmpty()) {
                sb.append(" [args:");
                for (var a : s.arguments) sb.append(" ").append(a.name).append(a.required ? "*" : "");
                sb.append("]");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    private String getToolsSummary() {
        StringBuilder sb = new StringBuilder();
        toolCatalog.all().forEach(t -> {
            sb.append("- ").append(t.name).append(": ").append(safe(t.description));
            if (t.arguments != null && !t.arguments.isEmpty()) {
                sb.append(" [args:");
                for (var a : t.arguments) sb.append(" ").append(a.name).append(a.required ? "*" : "");
                sb.append("]");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    private String getCapabilitiesSummary() {
        return getSkillsSummary() + "\n" + getToolsSummary();
    }

    private AgentAction tryParseAction(String text) {
        if (text == null || text.isBlank()) return null;
        ChatAction chatAction = ChatActionParser.parse(text);
        if (chatAction != null) {
            String act = chatAction.action();
            String name = chatAction.name();
            if (isCapability(act) && !isCapability(name)) return new AgentAction("tool", act, name, chatAction.args());
            if (isCapability(name)) return AgentAction.fromChatAction(chatAction);
        }
        try {
            String content = stripFence(text);
            String processed = content.replace("[action]", "action:").replace("[name]", "name:").replace("[args]", "args:");
            if (processed.contains("name:") || processed.contains("action:")) {
                processed = processed.lines().filter(l -> !l.trim().startsWith("-")).map(l -> l.replaceFirst("^\\s*tool:", "name:")).reduce((a, b) -> a + "\n" + b).orElse("");
                Map<String, Object> map = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().readValue(processed, Map.class);
                String name = String.valueOf(map.getOrDefault("name", map.getOrDefault("tool", "")));
                String action = String.valueOf(map.getOrDefault("action", "tool"));
                Object argsObj = map.getOrDefault("args", map.getOrDefault("params", Map.of()));
                Map<String, Object> args = argsObj instanceof Map ? (Map<String, Object>) argsObj : Map.of();
                if (isCapability(name)) return new AgentAction(action, name, "fallback", args);
                if (isCapability(action)) return new AgentAction("tool", action, name, args);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isCapability(String name) {
        if (name == null || name.isBlank()) return false;
        String n = name.trim();
        return toolCatalog.all().stream().anyMatch(t -> t.name.equals(n)) ||
               skillCatalog.all().stream().anyMatch(s -> s.name.equals(n));
    }

    private String stripFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:yaml|json|markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private String buildRepairKnowledgeContext(ChatSession session, ProblemKnowledgeStore knowledgeStore) {
        if (knowledgeStore == null || session == null) return "none";
        String query = "";
        for (int i = session.messages().size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(session.messages().get(i).role())) {
                query = session.messages().get(i).content();
                break;
            }
        }
        if (query.isBlank()) return "none";
        var records = knowledgeStore.searchSuccessfulRecords(query, 2);
        if (records.isEmpty()) return "none";
        return records.stream().map(r -> r.classification + ": " + r.repairScheme).reduce((a, b) -> a + " | " + b).orElse("none");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
