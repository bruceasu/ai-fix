package me.asu.ai.skill;

import java.util.ArrayList;
import java.util.List;
import me.asu.ai.tool.ToolArgumentDefinition;

public class SkillDefinition {
    public String name;
    public String description;
    public boolean autoExecuteAllowed;
    public List<ToolArgumentDefinition> arguments = new ArrayList<>();
    public List<SkillStepDefinition> steps = new ArrayList<>();
    public String skillHome;
    public String markdownHome;
    public String markdownDescription;
    public boolean generatedFromMarkdown;
}
