package me.asu.ai.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import me.asu.ai.config.AppConfig;

public class LLMFactory {

    public static LLMClient create(String provider, String model, AppConfig config) {
        return switch (provider) {
            case "ollama" -> new OllamaClientImpl(model, config.get("ollama.base.url", "http://localhost:11434"));
            case "groq" -> new SimpleOpenAIClientImpl(
                    createOpenAIClient(
                            config.getRequired("groq.api.key"),
                            config.get("groq.base.url", "https://api.groq.com/openai/v1")),
                    model);
            default -> new SimpleOpenAIClientImpl(
                    createOpenAIClient(
                            config.getRequired("openai.api.key"),
                            config.get("openai.base.url")),
                    model);
        };
    }

    private static OpenAIClient createOpenAIClient(String apiKey, String baseUrl) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
