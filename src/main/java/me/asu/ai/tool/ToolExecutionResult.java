package me.asu.ai.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolExecutionResult(
        boolean ok,
        String toolName,
        String output,
        Map<String, Object> data,
        String error) {

    public ToolExecutionResult {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static ToolExecutionResult success(String toolName, String output) {
        return success(toolName, output, Map.of());
    }

    public static ToolExecutionResult success(String toolName, String output, Map<String, Object> data) {
        return new ToolExecutionResult(true, toolName, output, data, "");
    }

    public static ToolExecutionResult failure(String toolName, String error) {
        return new ToolExecutionResult(false, toolName, "", Map.of(), error);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", ok);
        map.put("toolName", toolName);
        map.put("output", output);
        map.put("data", data);
        if (!error.isBlank()) {
            map.put("error", error);
        }
        return map;
    }
}
