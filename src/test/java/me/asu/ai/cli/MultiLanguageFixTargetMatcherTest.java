package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import me.asu.ai.fix.FixTargetMatcher;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;

class MultiLanguageFixTargetMatcherTest {

    @Test
    void matcherShouldSupportPythonContainerAndSymbolNames() {
        MethodInfo python = new MethodInfo();
        python.projectRoot = ".";
        python.language = "python";
        python.file = "app/service.py";
        python.packageName = "app.service";
        python.containerName = "app.service";
        python.className = "app.service";
        python.methodName = "build_report";
        python.symbolName = "build_report";
        python.symbolType = "function";
        python.signature = "def build_report(data):";
        python.annotations = List.of();
        python.calls = List.of("jsonify");
        python.beginLine = 10;
        python.endLine = 18;

        FixTargetMatcher matcher = new FixTargetMatcher();
        List<MethodInfo> results = matcher.filterTargets(
                List.of(python),
                "build report",
                "build_report",
                "app.service",
                "app.service",
                "service.py",
                "jsonify",
                null);

        assertEquals(1, results.size());
    }
}
