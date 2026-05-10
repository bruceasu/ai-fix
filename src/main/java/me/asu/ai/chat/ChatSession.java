package me.asu.ai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ChatSession {
    private static final int RECENT_MESSAGE_LIMIT = 8;
    private static final int MAX_CONTEXT_MESSAGE_CHARS = 1600;
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private final String name;
    private final List<ChatMessage> messages;
    private String summary;

    public ChatSession(String name) {
        this(name, new ArrayList<>(), "");
    }

    public ChatSession(String name, List<ChatMessage> messages) {
        this(name, messages, "");
    }

    public ChatSession(String name, List<ChatMessage> messages, String summary) {
        this.name = name;
        this.messages = messages;
        this.summary = summary == null ? "" : summary;
    }

    public String name() {
        return name;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public String summary() {
        return summary;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        summarizeIfNeeded();
    }

    public ChatSession rename(String newName) {
        return new ChatSession(newName, new ArrayList<>(messages), summary);
    }

    public String toTranscript() {
        StringBuilder sb = new StringBuilder();
        if (!summary.isBlank()) {
            sb.append("session-summary: ").append(summary).append("\n\n");
        }
        for (ChatMessage message : messages) {
            sb.append(message.role()).append(": ").append(sanitizeMessageContent(message.content())).append("\n\n");
        }
        return sb.toString().trim();
    }

    private void summarizeIfNeeded() {
        while (messages.size() > RECENT_MESSAGE_LIMIT) {
            ChatMessage removed = messages.remove(0);
            String condensed = removed.role() + ": " + compactMessage(removed.content());
            if (summary.isBlank()) {
                summary = condensed;
            } else {
                summary = summary + "\n" + condensed;
            }
            summary = trimSummary(summary, 4000);
        }
    }
      private static String trimSummary(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return "...[older summary truncated]\n" + text.substring(text.length() - maxChars);
    }

    private static String sanitizeMessageContent(String text) {
        return compactMessage(text).replace("\r", "");
    }

    
    private static String compactMessage(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r", "").trim();
        String specialized = compactToolResult(normalized);
        if (specialized != null) {
            return specialized;
        }
        if (normalized.length() <= MAX_CONTEXT_MESSAGE_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTEXT_MESSAGE_CHARS)
                + "\n...[truncated " + (normalized.length() - MAX_CONTEXT_MESSAGE_CHARS) + " chars]";
    }

    

    private static String compactToolResult(String text) {
        if (!text.startsWith("[action-result]")) {
            return null;
        }
        String payload = text.substring("[action-result]".length()).trim();
        try {
            Map<String, Object> root = JSON.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
            Object toolName = root.get("toolName");
            Object output = root.get("output");
            if ("web-fetch".equals(toolName)) {
                return summarizeWebFetch(output);
            }
            if ("read-file".equals(toolName)) {
                return summarizeReadFile(root, output);
            }
            if ("list-files".equals(toolName)) {
                return summarizeListFiles(root, output);
            }
            if ("youtube-transcript".equals(toolName)) {
                return summarizeYoutubeTranscript(root, output);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String summarizeListFiles(Map<String, Object> root, Object output) {
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        if (content.isBlank()) return "[action-result]\nlist-files: empty directory";
        String[] lines = content.split("\n");
        if (lines.length <= 15) return "[action-result]\nlist-files output:\n" + content;
        
        StringBuilder sb = new StringBuilder("[action-result]\nlist-files summary:\n");
        sb.append("Total files: ").append(lines.length).append("\n");
        sb.append("Sample files:\n");
        for (int i = 0; i < Math.min(10, lines.length); i++) {
            sb.append("- ").append(lines[i]).append("\n");
        }
        sb.append("... (and ").append(lines.length - 10).append(" more files)");
        return sb.toString();
    }

    private static String summarizeWebFetch(Object output) {
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        if (content.isBlank()) {
            return "[action-result]\nweb-fetch: empty content";
        }
        String excerpt = content.length() <= 600 ? content : content.substring(0, 600) + "...";
        return "[action-result]\nweb-fetch excerpt:\n" + excerpt;
    }

    @SuppressWarnings("unchecked")
    private static String summarizeReadFile(Map<String, Object> root, Object output) {
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String path = String.valueOf(data.getOrDefault("path", "unknown"));
        String excerpt = content.length() <= 600 ? content : content.substring(0, 600) + "...";
        return "[action-result]\nread-file snippet:\npath=" + path + "\n" + excerpt;
    }

    @SuppressWarnings("unchecked")
    private static String summarizeYoutubeTranscript(Map<String, Object> root, Object output) {
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Object videoIds = data.get("videoIds");
        String content = output == null ? "" : String.valueOf(output).replace("\r", "").trim();
        String excerpt = content.length() <= 700 ? content : content.substring(0, 700) + "...";
        return "[action-result]\nyoutube-transcript excerpt:\nvideoIds=" + videoIds + "\n" + excerpt;
    }

  
}