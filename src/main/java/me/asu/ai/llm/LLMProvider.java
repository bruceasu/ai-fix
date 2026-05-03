package me.asu.ai.llm;

import me.asu.ai.config.AppConfig;

public interface LLMProvider {

    String name();

    LLMClient create(String model, AppConfig config);
}
