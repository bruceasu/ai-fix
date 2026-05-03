package me.asu.ai.tool;

import java.nio.charset.StandardCharsets;

public class GitDiffSupport {

    public String readChangedFilesReviewInput() throws Exception {
        String status = runCommandCapture("git", "status", "--short");
        String diff = runCommandCapture("git", "diff", "--no-ext-diff", "HEAD", "--");
        if (status.isBlank() && diff.isBlank()) {
            return "No changed files detected.";
        }
        return """
                [git status --short]
                %s

                [git diff HEAD --]
                %s
                """.formatted(status.isBlank() ? "(empty)" : status.trim(), diff.isBlank() ? "(empty)" : diff.trim());
    }

    private String runCommandCapture(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd) + "\n" + out);
        }
        return out;
    }
}
