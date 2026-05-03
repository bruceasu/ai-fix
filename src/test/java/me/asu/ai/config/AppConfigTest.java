package me.asu.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

    @Test
    void shouldResolveConfiguredAppHomeAndResourceDirectories(@TempDir Path tempDir) throws IOException {
        Path appHome = tempDir.resolve("app-home");
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(
                configFile,
                """
                        app.home=%s
                        provider=groq
                        model=llama-3.1-8b-instant
                        """.formatted(toPropertiesPath(appHome)),
                StandardCharsets.UTF_8);

        AppConfig config = AppConfig.load(configFile.toString());

        assertEquals(appHome.toAbsolutePath().normalize(), config.getAppHomeDirectory());
        assertEquals(appHome.resolve("tools").normalize(), config.getToolsDirectory());
        assertEquals(appHome.resolve("skills").normalize(), config.getSkillsDirectory());
        assertEquals(appHome.resolve("knowledge").normalize(), config.getKnowledgeDirectory());
        assertEquals("groq", config.get("provider"));
        assertEquals("llama-3.1-8b-instant", config.get("model"));
        assertTrue(config.getLoadedSources().stream().anyMatch(source -> source.endsWith("ai-fix.properties")));
    }

    private String toPropertiesPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}
