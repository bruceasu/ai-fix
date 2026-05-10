package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.asu.ai.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonToolDispatcherSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void executesToolThroughPythonDispatcher() throws Exception {
        Path toolsDir = tempDir.resolve("tools");
        Path toolDir = toolsDir.resolve("demo-tool");
        Files.createDirectories(toolDir);

        Files.writeString(toolDir.resolve("tool.json"), """
                {
                  "name": "demo-tool",
                  "description": "Demo tool",
                  "autoExecuteAllowed": true,
                  "tool": {
                    "type": "external-command",
                    "program": "python",
                    "args": [
                      "${toolHome}/run.py",
                      "--message",
                      "${message}"
                    ]
                  },
                  "arguments": [
                    {
                      "name": "message",
                      "type": "string",
                      "description": "Demo message",
                      "required": true,
                      "defaultValue": ""
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(toolDir.resolve("run.py"), """
                import argparse
                import json

                parser = argparse.ArgumentParser()
                parser.add_argument("--message", required=True)
                args = parser.parse_args()

                print(json.dumps({
                    "ok": True,
                    "toolName": "demo-tool",
                    "output": args.message,
                    "data": {"message": args.message},
                    "error": ""
                }, ensure_ascii=False))
                """, StandardCharsets.UTF_8);

        Path pythonScriptsDir = Path.of("src/main/python").toAbsolutePath().normalize();
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(configFile, """
                tools.dir=%s
                python.scripts.dir=%s
                provider=
                model=
                """.formatted(toolsDir.toString().replace("\\", "/"), pythonScriptsDir.toString().replace("\\", "/")), StandardCharsets.UTF_8);

        AppConfig config = AppConfig.load(configFile.toString());
        PythonToolDispatcherSupport dispatcher = new PythonToolDispatcherSupport(config);

        ToolExecutionResult result = dispatcher.execute("demo-tool", "{\"message\":\"hello\"}", true, "", "");

        assertTrue(result.ok(), result.error());
        assertEquals("demo-tool", result.toolName());
        assertEquals("hello", result.output());
        assertEquals("hello", result.data().get("message"));
    }

    @Test
    void returnsMissingArgsFromPythonDispatcher() throws Exception {
        Path toolsDir = tempDir.resolve("tools");
        Path toolDir = toolsDir.resolve("demo-tool");
        Files.createDirectories(toolDir);

        Files.writeString(toolDir.resolve("tool.json"), """
                {
                  "name": "demo-tool",
                  "description": "Demo tool",
                  "autoExecuteAllowed": true,
                  "tool": {
                    "type": "external-command",
                    "program": "python",
                    "args": [
                      "${toolHome}/run.py",
                      "--message",
                      "${message}"
                    ]
                  },
                  "arguments": [
                    {
                      "name": "message",
                      "type": "string",
                      "description": "Demo message",
                      "required": true,
                      "defaultValue": ""
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        Files.writeString(toolDir.resolve("run.py"), "print('never runs')", StandardCharsets.UTF_8);

        Path pythonScriptsDir = Path.of("src/main/python").toAbsolutePath().normalize();
        Path configFile = tempDir.resolve("ai-fix.properties");
        Files.writeString(configFile, """
                tools.dir=%s
                python.scripts.dir=%s
                provider=
                model=
                """.formatted(toolsDir.toString().replace("\\", "/"), pythonScriptsDir.toString().replace("\\", "/")), StandardCharsets.UTF_8);

        AppConfig config = AppConfig.load(configFile.toString());
        PythonToolDispatcherSupport dispatcher = new PythonToolDispatcherSupport(config);

        ToolExecutionResult result = dispatcher.execute("demo-tool", "{}", true, "", "");

        assertFalse(result.ok());
        assertTrue(result.error().contains("Missing required arguments"));
    }
}
