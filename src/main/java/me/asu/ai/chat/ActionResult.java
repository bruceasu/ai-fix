package me.asu.ai.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized result of an Agent action, providing feedback to the Orchestrator and LLM.
 */
public record ActionResult(
        boolean success,
        String output,
        Map<String, Object> data,
        String error,
        String hints // Optional hints or suggestions for the next step
) {
    public ActionResult {
        if (data == null) {
            data = new LinkedHashMap<>();
        }
    }

    public static ActionResult success(String output, Map<String, Object> data) {
        return new ActionResult(true, output, data, null, null);
    }

    public static ActionResult success(String output, Map<String, Object> data, String hints) {
        return new ActionResult(true, output, data, null, hints);
    }

    public static ActionResult failure(String error) {
        return new ActionResult(false, null, null, error, null);
    }
}
