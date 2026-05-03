package me.asu.ai.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import me.asu.ai.config.AppConfig;

public class LLMProviderRegistry {

    private final Map<String, LLMProvider> providers = new LinkedHashMap<>();

    public static LLMProviderRegistry createDefault() {
        LLMProviderRegistry registry = new LLMProviderRegistry();
        registry.register(new OpenAIProvider());
        registry.register(new GroqProvider());
        registry.register(new OllamaProvider());
        return registry;
    }

    public void register(LLMProvider provider) {
        providers.put(normalize(provider.name()), provider);
    }

    public LLMClient create(String providerName, String model, AppConfig config) {
        String normalized = normalize(providerName == null || providerName.isBlank()
                ? config.get("provider", AppConfig.DEFAULT_PROVIDER)
                : providerName);
        LLMProvider provider = providers.get(normalized);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
        String effectiveModel = model == null || model.isBlank()
                ? config.get("model", AppConfig.DEFAULT_MODEL)
                : model;
        return provider.create(effectiveModel, config);
    }

    private String normalize(String providerName) {
        return providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
    }

    private static OpenAIClient createOpenAIClient(String apiKey, String baseUrl) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private static final class OpenAIProvider implements LLMProvider {
        @Override
        public String name() {
            return "openai";
        }

        @Override
        public LLMClient create(String model, AppConfig config) {
            return new SimpleOpenAIClientImpl(
                    createOpenAIClient(
                            config.getRequired("openai.api.key"),
                            config.get("openai.base.url")),
                    model);
        }
    }

    private static final class GroqProvider implements LLMProvider {
        @Override
        public String name() {
            return "groq";
        }

        @Override
        public LLMClient create(String model, AppConfig config) {
            return new SimpleOpenAIClientImpl(
                    createOpenAIClient(
                            config.getRequired("groq.api.key"),
                            config.get("groq.base.url", AppConfig.DEFAULT_GROQ_BASE_URL)),
                    model);
        }
    }

    private static final class OllamaProvider implements LLMProvider {
        @Override
        public String name() {
            return "ollama";
        }

        @Override
        public LLMClient create(String model, AppConfig config) {
            return new OllamaClientImpl(model, config.get("ollama.base.url", AppConfig.DEFAULT_OLLAMA_BASE_URL));
        }
    }
}
