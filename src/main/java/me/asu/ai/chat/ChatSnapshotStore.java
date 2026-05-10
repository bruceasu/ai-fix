package me.asu.ai.chat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


import me.asu.ai.config.AppConfig;

public final class ChatSnapshotStore {
    private final AppConfig config;

    public ChatSnapshotStore(AppConfig config) {
        this.config = config;
    }

    public void store(ChatSession session) throws Exception {
        Path dir = config.getSessionsDirectory();
        Files.createDirectories(dir);
        Path file = dir.resolve(session.name() + ".md");
        StringBuilder sb = new StringBuilder();
        sb.append("# ai-fix chat session\n\n");
        sb.append("- name: ").append(session.name()).append("\n\n");
        if (!session.summary().isBlank()) {
            sb.append("## session-summary\n\n");
            sb.append(session.summary()).append("\n\n");
        }
        for (ChatMessage message : session.messages()) {
            sb.append("## ").append(message.role()).append("\n\n");
            sb.append(message.content()).append("\n\n");
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public ChatSession load(String name) throws Exception {
        Path file = config.getSessionsDirectory().resolve(name + ".md").normalize();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<ChatMessage> messages = new ArrayList<>();
        String role = null;
        StringBuilder body = new StringBuilder();
        String summary = "";
        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (role != null) {
                    if ("session-summary".equals(role)) {
                        summary = body.toString().trim();
                    } else {
                        messages.add(new ChatMessage(role, body.toString().trim()));
                    }
                }
                role = line.substring(3).trim();
                body = new StringBuilder();
                continue;
            }
            if (role != null) {
                body.append(line).append("\n");
            }
        }
        if (role != null) {
            if ("session-summary".equals(role)) {
                summary = body.toString().trim();
            } else {
                messages.add(new ChatMessage(role, body.toString().trim()));
            }
        }
        return new ChatSession(name, messages, summary);
    }

    public List<String> listSessionNames() {
        try {
            Path dir = config.getSessionsDirectory();
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".md"))
                    .map(name -> name.substring(0, name.length() - 3))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
