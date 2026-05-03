package me.asu.ai.llm;

import me.asu.ai.config.AppConfig;

public class LLMFactory {

    private static final LLMProviderRegistry DEFAULT_REGISTRY = LLMProviderRegistry.createDefault();

    public static LLMClient create(String provider, String model, AppConfig config) {
        return DEFAULT_REGISTRY.create(provider, model, config);
    }
}
