package me.asu.ai.util;

import java.nio.charset.StandardCharsets;

public class GitWorktreeSupport {

    public boolean isGitRepo() {
        try {
            runCommand("git", "rev-parse", "--is-inside-work-tree");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void ensureGitRepo() throws Exception {
        runCommand("git", "rev-parse", "--is-inside-work-tree");
    }

    public boolean hasUncommittedChanges() throws Exception {
        String status = runCommandCapture("git", "status", "--porcelain");
        return !status.isBlank();
    }

    public void ensureWorkingTreeClean() throws Exception {
        if (hasUncommittedChanges()) {
            throw new RuntimeException("Working tree not clean. Use --stash");
        }
    }

    public void autoStash() throws Exception {
        if (hasUncommittedChanges()) {
            runCommand("git", "stash", "push", "-u", "-m", "ai-fix-auto");
        }
    }

    public void checkoutNewBranch(String branch) throws Exception {
        runCommand("git", "checkout", "-b", branch);
    }

    public void commitAll(String message) throws Exception {
        runCommand("git", "add", ".");
        runCommand("git", "commit", "-m", message);
    }

    public void popStashQuietly() {
        try {
            runCommand("git", "stash", "pop");
        } catch (Exception e) {
            System.out.println("stash pop failed");
        }
    }

    private void runCommand(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd) + "\n" + out);
        }
    }

    private String runCommandCapture(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }
}
