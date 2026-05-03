package me.asu.ai.tool;

import java.util.ArrayList;
import java.util.List;

public class ToolRuntimeSpec {
    public String type;
    public String command;
    public String program;
    public List<String> args = new ArrayList<>();
    public String workingDirectory;
}
