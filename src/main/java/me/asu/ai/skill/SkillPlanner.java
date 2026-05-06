package me.asu.ai.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.tool.ToolArgumentDefinition;

public class SkillPlanner {
    private final ObjectMapper mapper = new ObjectMapper();

    public SkillPlan plan(SkillDefinition skill, String inputJson) throws Exception {
        Map<String, Object> input = mapper.readValue(
                inputJson == null || inputJson.isBlank() ? "{}" : inputJson,
                new TypeReference<Map<String, Object>>() {
                });
        if (input == null) {
            input = new LinkedHashMap<>();
        }
        applyParameterAliases(input, skill.parameterAliases);
        applyDefaultValues(input, skill.arguments);
        return new SkillPlan(skill, input);
    }

    static void applyParameterAliases(Map<String, Object> input, Map<String, String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String aliasName = alias.getKey();
            String canonicalName = alias.getValue();
            if (canonicalName == null || canonicalName.isBlank()) {
                continue;
            }
            if (input.containsKey(aliasName) && !input.containsKey(canonicalName)) {
                input.put(canonicalName, input.remove(aliasName));
            }
        }
    }

    static void applyDefaultValues(Map<String, Object> input, List<ToolArgumentDefinition> arguments) {
        if (arguments == null) {
            return;
        }
        for (ToolArgumentDefinition arg : arguments) {
            if (arg.defaultValue != null && !arg.defaultValue.isBlank() && !input.containsKey(arg.name)) {
                input.put(arg.name, arg.defaultValue);
            }
        }
    }
}
