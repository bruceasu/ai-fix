package me.asu.ai.chat;

import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.tool.ToolCatalogService;

public final class ChatCompleter implements Completer {
    private final ToolCatalogService toolCatalog;
    private final SkillCatalogService skillCatalog;
    private final ChatSnapshotStore snapshotStore;

    public ChatCompleter(
            ToolCatalogService toolCatalog,
            SkillCatalogService skillCatalog,
            ChatSnapshotStore snapshotStore) {
        this.toolCatalog = toolCatalog;
        this.skillCatalog = skillCatalog;
        this.snapshotStore = snapshotStore;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word().trim();
        String full = line.line().trim();

        if (full.startsWith("/store ")) {
            candidates.add(new Candidate("demo-session"));
            return;
        }
        if (full.startsWith("/load ")) {
            snapshotStore.listSessionNames().forEach(name -> candidates.add(new Candidate(name)));
            return;
        }
        if (full.startsWith("/new-skill ")) {
            candidates.add(new Candidate("my-new-skill"));
            return;
        }
        if (full.startsWith("/list-skills")) {
            skillCatalog.all().stream()
                    .map(skill -> "skill:" + skill.name)
                    .forEach(value -> candidates.add(new Candidate(value)));
            return;
        }
        if (full.startsWith("/list-tools")) {
            toolCatalog.all().stream()
                    .map(tool -> "tool:" + tool.name)
                    .forEach(value -> candidates.add(new Candidate(value)));
            return;
        }
        if (word.startsWith("/")) {
            List.of("/help", "/store", "/load", "/new-skill", "/list-skills", "/list-tools", "/exit")
                    .forEach(command -> candidates.add(new Candidate(command)));
            return;
        }

        toolCatalog.all().stream()
                .map(tool -> "tool:" + tool.name)
                .forEach(value -> candidates.add(new Candidate(value)));
        skillCatalog.all().stream()
                .map(skill -> "skill:" + skill.name)
                .forEach(value -> candidates.add(new Candidate(value)));
    }
}