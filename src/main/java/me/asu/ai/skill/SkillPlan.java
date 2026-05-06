package me.asu.ai.skill;

import java.util.List;
import java.util.Map;

public record SkillPlan(SkillDefinition skill, Map<String, Object> input) {
    public List<SkillStepDefinition> steps() {
        return skill.steps;
    }
}
