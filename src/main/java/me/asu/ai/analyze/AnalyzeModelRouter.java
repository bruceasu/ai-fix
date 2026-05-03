package me.asu.ai.analyze;

import me.asu.ai.config.AppConfig;

public final class AnalyzeModelRouter {

    private static final int SMALL_INPUT_THRESHOLD = 8_000;

    private AnalyzeModelRouter() {
    }

    public static Selection select(String requestedProvider, String requestedModel, AnalyzeInputType type, String processedInput, AppConfig config) {
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            return new Selection(
                    requestedProvider,
                    firstNonBlank(requestedModel, config.get("model"), defaultModelForProvider(requestedProvider)));
        }

        String provider = requestedProvider;
        String model = requestedModel;
        int length = processedInput == null ? 0 : processedInput.length();

        if (length < SMALL_INPUT_THRESHOLD && hasProviderConfig("ollama", config)) {
            provider = "ollama";
            model = firstNonBlank(model, config.get("analyze.model.ollama"), config.get("ollama.model"), "qwen2.5-coder:7b");
        } else if (type == AnalyzeInputType.JAVA_EXCEPTION) {
            provider = preferredCloudProvider(config);
            model = firstNonBlank(model, config.get("analyze.model.code"), config.get("model"), "gpt-4.1");
        } else if (length >= SMALL_INPUT_THRESHOLD && config.get("analyze.model.large") != null) {
            provider = firstNonBlank(config.get("analyze.provider.large"), provider, preferredCloudProvider(config));
            model = config.get("analyze.model.large");
        } else {
            provider = firstNonBlank(provider, config.get("provider"), "openai");
            model = firstNonBlank(model, config.get("model"), "gpt-4.1");
        }

        return new Selection(provider, model);
    }

    private static String preferredCloudProvider(AppConfig config) {
        if (hasProviderConfig("openai", config)) {
            return "openai";
        }
        if (hasProviderConfig("groq", config)) {
            return "groq";
        }
        return firstNonBlank(config.get("provider"), "openai");
    }

    private static String defaultModelForProvider(String provider) {
        return switch (provider) {
            case "ollama" -> "qwen2.5-coder:7b";
            case "groq" -> "llama-3.1-70b-versatile";
            default -> "gpt-4.1";
        };
    }

    private static boolean hasProviderConfig(String provider, AppConfig config) {
        return switch (provider) {
            case "ollama" -> config.get("ollama.base.url") != null;
            case "groq" -> config.get("groq.api.key") != null;
            case "openai" -> config.get("openai.api.key") != null;
            default -> false;
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record Selection(String provider, String model) {
    }
}
