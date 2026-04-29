package me.asu.ai.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;

class FixTargetMatcherTest {

    private final FixTargetMatcher matcher = new FixTargetMatcher();

    @Test
    void applyMatchQueryShouldRequireAllTermsAndSortByScore() {
        MethodInfo exact = method("src/main/java/me/asu/ai/fix/FixTargetMatcher.java", "FixTargetMatcher", "filterTargets",
                "filterTargets(List<MethodInfo>)", List.of(), List.of());
        MethodInfo weaker = method("src/main/java/me/asu/ai/fix/FixCliOptions.java", "FixCliOptions", "normalize",
                "normalize()", List.of(), List.of("filterTargets"));
        MethodInfo miss = method("src/main/java/me/asu/ai/cli/MainCli.java", "MainCli", "main",
                "main(String[] args)", List.of(), List.of());

        List<MethodInfo> results = matcher.applyMatchQuery(List.of(weaker, miss, exact), "filter targets");

        assertEquals(2, results.size());
        assertIterableEquals(List.of(exact, weaker), results);
    }

    @Test
    void filterTargetsShouldSupportPackageClassAndCallFilters() {
        MethodInfo target = method("src/main/java/me/asu/ai/fix/FixTargetMatcher.java", "FixTargetMatcher", "scoreMethod",
                "scoreMethod(MethodInfo,List<String>)", List.of("VisibleForTesting"), List.of("containsIgnoreCase"));
        MethodInfo other = method("src/main/java/me/asu/ai/cli/AiFixCLI.java", "AiFixCLI", "main",
                "main(String[] args)", List.of(), List.of("scoreMethod"));

        List<MethodInfo> results = matcher.filterTargets(
                List.of(target, other),
                "score method",
                "scoreMethod",
                "me.asu.ai.fix",
                "FixTargetMatcher",
                "FixTargetMatcher.java",
                "containsIgnoreCase",
                "VisibleForTesting");

        assertEquals(1, results.size());
        assertEquals(target, results.get(0));
    }

    @Test
    void scoreMethodShouldRewardMethodNameHits() {
        MethodInfo method = method("src/main/java/me/asu/ai/fix/FixTargetMatcher.java", "FixTargetMatcher", "scoreMethod",
                "scoreMethod(MethodInfo,List<String>)", List.of(), List.of("containsIgnoreCase"));

        int exactScore = matcher.scoreMethod(method, List.of("scoremethod"));
        int weakerScore = matcher.scoreMethod(method, List.of("containsignorecase"));

        assertTrue(exactScore > weakerScore);
    }

    private MethodInfo method(
            String file,
            String className,
            String methodName,
            String signature,
            List<String> annotations,
            List<String> calls) {
        MethodInfo method = new MethodInfo();
        method.projectRoot = ".";
        method.file = file;
        method.className = className;
        method.methodName = methodName;
        method.signature = signature;
        method.annotations = annotations;
        method.calls = calls;
        method.beginLine = 10;
        method.endLine = 20;
        return method;
    }
}
