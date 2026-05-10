package me.asu.ai.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a structured action requested by the AI Agent.
 */
public record AgentAction(
        String action,   // tool or skill
        String name,     // name of the tool or skill
        String reason,   // internal reasoning for this action
        Map<String, Object> args
) {
    public AgentAction {
        if (args == null) {
            args = new LinkedHashMap<>();
        }
    }

    public static AgentAction fromChatAction(ChatAction chatAction) {
        return new AgentAction(
                chatAction.action(),
                chatAction.name(),
                chatAction.reason(),
                chatAction.args()
        );
    }
}
