package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalCommandSupportTest {

    @Test
    void shouldExecuteExternalPythonScript(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("echo.py");
        Files.writeString(
                script,
                """
                        import argparse
                        parser = argparse.ArgumentParser()
                        parser.add_argument("--value", default="")
                        args = parser.parse_args()
                        print("tool:" + args.value)
                        """,
                StandardCharsets.UTF_8);

        ToolDefinition definition = new ToolDefinition();
        definition.name = "demo-external";
        definition.toolHome = tempDir.resolve("tool.yaml").toString();
        definition.tool.type = "external-command";
        definition.tool.program = "python";
        definition.tool.args = java.util.List.of("./echo.py", "--value", "${value}");

        ToolExecutionResult result = new ExternalCommandSupport().execute(
                definition,
                new ObjectMapper().readTree("{\"value\":\"hello\"}"));

        assertTrue(result.ok());
        assertEquals("tool:hello", result.output());
    }
}
