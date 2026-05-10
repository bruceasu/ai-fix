package me.asu.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class ChatActionParser {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public static ChatAction parse(String text) {
        if (text == null || text.isBlank())
            return null;

        try {
            String yaml = extractYaml(text);
            if (yaml == null)
                return null;

            ChatAction action = YAML.readValue(yaml, ChatAction.class);
            if (action != null && action.action() != null && !action.action().isBlank()) {
                return action;
            }
        } catch (Exception e) {
            // Silently ignore parsing errors for conversational text
        }
        return null;
    }

    private static String extractYaml(String text) {
        // Find ```yaml ... ```
        int start = text.indexOf("```yaml");
        if (start >= 0) {
            int end = text.indexOf("```", start + 7);
            if (end > start) {
                return text.substring(start + 7, end).trim();
            }
        }

        // Fallback: look for "action:" if not in a fence
        int actionIndex = text.indexOf("action:");
        if (actionIndex >= 0) {
            // Find end of YAML block or end of text
            int nextFence = text.indexOf("```", actionIndex);
            if (nextFence > actionIndex) {
                return text.substring(actionIndex, nextFence).trim();
            }
            return text.substring(actionIndex).trim();
        }
        return null;
    }
}
