package me.asu.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public interface LLMClient {

    ObjectMapper JSON_MAPPER = new ObjectMapper();
    ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    String generate(String prompt);

    default <T> T generateYaml(String prompt, Class<T> type) {
        try {
            String normalized = stripFence(generate(prompt));
            return YAML_MAPPER.readValue(normalized, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse YAML response", e);
        }
    }

    default <T> T generateJson(String prompt, Class<T> type) {
        try {
            String normalized = stripFence(generate(prompt));
            return JSON_MAPPER.readValue(normalized, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON response", e);
        }
    }

    default String generateText(String prompt) {
        return generate(prompt);
    }

    private static String stripFence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:yaml|json|markdown|md|text)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}
