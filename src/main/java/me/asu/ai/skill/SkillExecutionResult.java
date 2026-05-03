package me.asu.ai.skill;

import java.util.LinkedHashMap;
import java.util.Map;

public record SkillExecutionResult(
        boolean ok,
        String skillName,
        Map<String, Object> context,
        Map<String, Object> result,
        String summary,
        String error) {

    public SkillExecutionResult {
        context = context == null ? Map.of() : Map.copyOf(context);
        result = result == null ? Map.of() : Map.copyOf(result);
        summary = summary == null ? "" : summary;
    }

    public static SkillExecutionResult success(String skillName, Map<String, Object> context) {
        return success(skillName, context, context, "");
    }

    public static SkillExecutionResult success(
            String skillName,
            Map<String, Object> context,
            Map<String, Object> result,
            String summary) {
        return new SkillExecutionResult(true, skillName, context, result, summary, "");
    }

    public static SkillExecutionResult failure(String skillName, String error) {
        return new SkillExecutionResult(false, skillName, Map.of(), Map.of(), "", error);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", ok);
        map.put("skillName", skillName);
        map.put("summary", summary);
        map.put("result", result);
        map.put("context", context);
        if (!error.isBlank()) {
            map.put("error", error);
        }
        return map;
    }
}
