package me.asu.ai.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;

public class SkillExecutor {
    private final ToolExecutor toolExecutor;
    private final String provider;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public SkillExecutor(ToolExecutor toolExecutor, String provider, String model) {
        this.toolExecutor = toolExecutor;
        this.provider = provider;
        this.model = model;
    }

    public ExecutionOutcome execute(SkillPlan plan, boolean confirmed) {
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("input", plan.input());

            for (SkillStepDefinition step : plan.steps()) {
                if ("ai".equals(step.type)) {
                    return ExecutionOutcome.failed(
                            "Legacy ai step is no longer supported. Migrate this skill to toolName=llm.");
                }
                if ("tool".equals(step.type)) {
                    Map<String, String> args = buildToolArguments(step, context);
                    ToolExecutionResult result = toolExecutor.execute(
                            step.toolName,
                            mapper.writeValueAsString(args),
                            confirmed || plan.skill().autoExecuteAllowed);
                    if (!result.ok()) {
                        if (step.optional) {
                            context.put(outputKey(step), "");
                            continue;
                        }
                        return ExecutionOutcome.failed(result.error());
                    }
                    context.put(outputKey(step), result.output());
                    context.put(outputKey(step) + "Data", result.data());
                    continue;
                }
                return ExecutionOutcome.failed("Unsupported step type: " + step.type);
            }
            return ExecutionOutcome.success(context);
        } catch (Exception e) {
            return ExecutionOutcome.failed(e.getMessage());
        }
    }

    private Map<String, String> buildToolArguments(SkillStepDefinition step, Map<String, Object> context) {
        Map<String, String> args = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : step.arguments.entrySet()) {
            args.put(entry.getKey(), resolveTemplate(entry.getValue(), context));
        }
        if ("llm".equals(step.toolName)) {
            if (!args.containsKey("provider") || args.get("provider") == null || args.get("provider").isBlank()) {
                String effectiveProvider = step.provider != null && !step.provider.isBlank() ? step.provider : provider;
                if (effectiveProvider != null && !effectiveProvider.isBlank()) {
                    args.put("provider", effectiveProvider);
                }
            }
            if (!args.containsKey("model") || args.get("model") == null || args.get("model").isBlank()) {
                if (model != null && !model.isBlank()) {
                    args.put("model", model);
                }
            }
        }
        return args;
    }

    private String resolveTemplate(String template, Map<String, Object> context) {
        if (template == null || template.isBlank()) {
            return "";
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        StringBuilder sb = new StringBuilder();
        
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = resolveValue(key, context);
            matcher.appendReplacement(sb, value != null ? java.util.regex.Matcher.quoteReplacement(String.valueOf(value)) : "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Object resolveValue(String key, Map<String, Object> context) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.", 2);
            Object obj = context.get(parts[0]);
            if (obj instanceof Map<?, ?> map) {
                return map.get(parts[1]);
            }
            return null;
        }
        return context.get(key);
    }

    private String outputKey(SkillStepDefinition step) {
        return step.outputKey == null || step.outputKey.isBlank() ? step.id : step.outputKey;
    }

    public record ExecutionOutcome(boolean success, Map<String, Object> context, String error) {
        public static ExecutionOutcome success(Map<String, Object> context) {
            return new ExecutionOutcome(true, context, "");
        }

        public static ExecutionOutcome failed(String error) {
            return new ExecutionOutcome(false, Map.of(), error == null ? "" : error);
        }
    }
}
