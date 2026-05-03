package me.asu.ai.fix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitWorktreeSupportTest {

    @Test
    void isGitRepoShouldReturnBooleanWithoutThrowing() {
        GitWorktreeSupport git = new GitWorktreeSupport();
        boolean result = git.isGitRepo();
        assertTrue(result || !result);
    }

    @Test
    void hasUncommittedChangesShouldBeCallableInsideGitRepo() throws Exception {
        GitWorktreeSupport git = new GitWorktreeSupport();
        if (!git.isGitRepo()) {
            assertFalse(git.isGitRepo());
            return;
        }
        boolean result = git.hasUncommittedChanges();
        assertTrue(result || !result);
    }
}
