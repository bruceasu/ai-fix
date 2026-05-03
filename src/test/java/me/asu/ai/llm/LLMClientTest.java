package me.asu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LLMClientTest {

    @Test
    void shouldParseYamlStructuredResponse() {
        LLMClient client = prompt -> """
                ```yaml
                action: tool
                name: read-file
                args:
                  path: D:/demo.txt
                ```
                """;

        Action action = client.generateYaml("ignored", Action.class);

        assertEquals("tool", action.action);
        assertEquals("read-file", action.name);
        assertEquals("D:/demo.txt", action.args.get("path"));
    }

    @Test
    void shouldParseJsonStructuredResponse() {
        LLMClient client = prompt -> """
                ```json
                {"action":"tool","name":"read-file","args":{"path":"D:/demo.txt"}}
                ```
                """;

        Action action = client.generateJson("ignored", Action.class);

        assertEquals("tool", action.action);
        assertEquals("read-file", action.name);
        assertEquals("D:/demo.txt", action.args.get("path"));
    }

    public static class Action {
        public String action;
        public String name;
        public java.util.Map<String, Object> args;
    }
}
