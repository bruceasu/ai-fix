package me.asu.ai.chat;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChatAction(String action, String name, String reason, String expectedOutput, Map<String, Object> args) {
    public ChatAction {
            if (action == null) {
                action = "";
            }
            if (name == null) {
                name = "";
            }
            if (reason == null) {
                reason = "";
            }
            if (expectedOutput == null) {
                expectedOutput = "";
            }
            if (args == null) {
                args = new LinkedHashMap<>();
            }
        }
        
}
