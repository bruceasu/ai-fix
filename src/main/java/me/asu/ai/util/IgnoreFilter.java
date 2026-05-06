package me.asu.ai.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IgnoreFilter {

    private final List<Pattern> patterns = new ArrayList<>();
    private final Path root;

    public IgnoreFilter(Path root) {
        this.root = root.toAbsolutePath().normalize();
        loadGitIgnore();
        // Default ignores
        patterns.add(compile(".git/"));
        patterns.add(compile("target/"));
        patterns.add(compile("bin/"));
        patterns.add(compile(".idea/"));
        patterns.add(compile(".vscode/"));
        patterns.add(compile(".settings/"));
    }

    private void loadGitIgnore() {
        Path gitIgnore = root.resolve(".gitignore");
        if (Files.exists(gitIgnore)) {
            try {
                List<String> lines = Files.readAllLines(gitIgnore);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    patterns.add(compile(line));
                }
            } catch (IOException e) {
                System.err.println("Warning: failed to read .gitignore: " + e.getMessage());
            }
        }
    }

    private Pattern compile(String glob) {
        String regex = glob.replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
                .replace("/", "[\\\\/]");
        if (glob.endsWith("/")) {
            regex = regex + ".*";
        }
        return Pattern.compile(".*" + regex + ".*");
    }

    public boolean shouldIgnore(Path path) {
        String relative = root.relativize(path.toAbsolutePath().normalize()).toString().replace("\\", "/");
        for (Pattern p : patterns) {
            if (p.matcher(relative).matches()) {
                return true;
            }
        }
        return false;
    }
}
