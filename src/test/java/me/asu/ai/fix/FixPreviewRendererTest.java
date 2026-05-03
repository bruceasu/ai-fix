package me.asu.ai.fix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.asu.ai.config.AppConfig;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;

class FixPreviewRendererTest {

    @Test
    void buildPreviewBlockShouldPreferMatchedLinesAndShowAbsoluteLineNumbers() throws IOException {
        Path projectRoot = Files.createTempDirectory("ai-fix-preview");
        Path sourceFile = projectRoot.resolve("src/main/java/sample/Demo.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(
                sourceFile,
                String.join("\n",
                        "package sample;",
                        "public class Demo {",
                        "    void render() {",
                        "        String value = \"first\";",
                        "        helper();",
                        "        helperRetry();",
                        "        int retries = 2;",
                        "        log.info(\"retry now\");",
                        "        finish();",
                        "    }",
                        "}"),
                StandardCharsets.UTF_8);

        MethodInfo method = new MethodInfo();
        method.projectRoot = projectRoot.toString();
        method.file = "src/main/java/sample/Demo.java";
        method.className = "Demo";
        method.methodName = "render";
        method.signature = "render()";
        method.beginLine = 3;
        method.endLine = 10;

        FixPreviewRenderer renderer = new FixPreviewRenderer(AppConfig.load("missing-test-config.properties"), 4,
                new FixTargetMatcher());

        String preview = renderer.buildPreviewBlock(method, "retry");

        assertTrue(preview.contains("   6 |         helper[[Retry]]();"));
        assertTrue(preview.contains("   4 |         String value = \"first\";"));
        assertTrue(preview.contains("   5 |         helper();"));
        assertTrue(preview.contains("   7 |         int retries = 2;"));
        assertTrue(preview.contains("..."));
        assertFalse(preview.contains("   3 |"));
        assertFalse(preview.contains("   8 |"));
    }

    @Test
    void formatCandidateLineShouldHighlightMatchedTermsAndShowScore() {
        MethodInfo method = new MethodInfo();
        method.projectRoot = ".";
        method.language = "python";
        method.file = "src/main/python/demo/app.py";
        method.packageName = "demo.app";
        method.containerName = "demo.app";
        method.className = "demo.app";
        method.methodName = "build_preview_block";
        method.symbolName = "build_preview_block";
        method.symbolType = "function";
        method.signature = "def build_preview_block(method, query):";
        method.beginLine = 10;
        method.endLine = 20;

        FixPreviewRenderer renderer = new FixPreviewRenderer(AppConfig.load("missing-test-config.properties"), 4,
                new FixTargetMatcher());

        String line = renderer.formatCandidateLine(2, method, "preview block");

        assertTrue(line.contains("2. demo.app#build_[[preview]]_[[block]]"));
        assertTrue(line.contains("score="));
        assertTrue(line.contains("signature=def build_[[preview]]_[[block]](method, query):"));
    }
}
