package me.asu.ai.tool;

import java.util.ArrayList;
import java.util.List;

public class ToolDefinition {
    public String name;
    public String description;
    public boolean autoExecuteAllowed;
    public ToolRuntimeSpec tool = new ToolRuntimeSpec();
    public List<ToolArgumentDefinition> arguments = new ArrayList<>();
    public String toolHome;
    // Optional markdown documentation attached from workspace or classpath
    public String markdownHome;
    public String markdownDescription;
}
