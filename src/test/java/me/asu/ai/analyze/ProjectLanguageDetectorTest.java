package me.asu.ai.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectLanguageDetectorTest {

    @Test
    void shouldDetectGoProjectFromGoMod() throws Exception {
        Path root = Files.createTempDirectory("lang-go");
        Files.writeString(root.resolve("go.mod"), "module example.com/demo", StandardCharsets.UTF_8);
        assertEquals(ProjectLanguage.GO, ProjectLanguageDetector.detect(root));
    }

    @Test
    void shouldDetectPythonProjectFromPyproject() throws Exception {
        Path root = Files.createTempDirectory("lang-py");
        Files.writeString(root.resolve("pyproject.toml"), "[project]\nname='demo'", StandardCharsets.UTF_8);
        assertEquals(ProjectLanguage.PYTHON, ProjectLanguageDetector.detect(root));
    }

    @Test
    void shouldDetectJavaProjectFromPom() throws Exception {
        Path root = Files.createTempDirectory("lang-java");
        Files.writeString(root.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        assertEquals(ProjectLanguage.JAVA, ProjectLanguageDetector.detect(root));
    }

    @Test
    void parseOrDetectShouldHonorExplicitLanguage() throws Exception {
        Path root = Files.createTempDirectory("lang-explicit");
        Files.writeString(root.resolve("go.mod"), "module example.com/demo", StandardCharsets.UTF_8);
        assertEquals(ProjectLanguage.PYTHON, ProjectLanguageDetector.parseOrDetect("python", root));
    }
}
