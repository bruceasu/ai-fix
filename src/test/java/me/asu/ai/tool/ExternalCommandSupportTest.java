package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalCommandSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void parsesJsonEnvelopeFromPythonTool() throws Exception {
        Path script = tempDir.resolve("tool.py");
        Files.writeString(script, """
                import json
                print(json.dumps({
                    "ok": True,
                    "toolName": "demo-tool",
                    "output": "hello",
                    "data": {"count": 1},
                    "error": ""
                }, ensure_ascii=False))
                """, StandardCharsets.UTF_8);

        ToolDefinition definition = new ToolDefinition();
        definition.name = "demo-tool";
        definition.toolHome = tempDir.toString();
        definition.tool.type = "external-command";
        definition.tool.program = "python";
        definition.tool.args = List.of(script.toString());

        ToolExecutionResult result = new ExternalCommandSupport().execute(definition, MAPPER.readTree("{}"));

        assertTrue(result.ok());
        assertEquals("demo-tool", result.toolName());
        assertEquals("hello", result.output());
        assertEquals(1, ((Number) result.data().get("count")).intValue());
    }

    @Test
    void fallsBackToRawStdoutWhenEnvelopeIsMissing() throws Exception {
        Path script = tempDir.resolve("plain.py");
        Files.writeString(script, "print('plain text')", StandardCharsets.UTF_8);

        ToolDefinition definition = new ToolDefinition();
        definition.name = "plain-tool";
        definition.toolHome = tempDir.toString();
        definition.tool.type = "external-command";
        definition.tool.program = "python";
        definition.tool.args = List.of(script.toString());

        ToolExecutionResult result = new ExternalCommandSupport().execute(definition, MAPPER.readTree("{}"));

        assertTrue(result.ok());
        assertEquals("plain text", result.output());
        assertFalse(result.data().isEmpty());
        assertEquals("external-command", result.data().get("type"));
    }
}
