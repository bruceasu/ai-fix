package me.asu.ai.skill;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkillStepDefinition {
    public String id;
    public String type;
    public boolean optional;
    public String provider;
    public String systemPrompt;
    public String userPrompt;
    public String outputKey;
    public String toolName;
    public Map<String, String> arguments = new LinkedHashMap<>();
}
