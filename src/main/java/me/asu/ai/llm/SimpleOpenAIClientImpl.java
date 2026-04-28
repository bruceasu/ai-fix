package me.asu.ai.llm;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class SimpleOpenAIClientImpl implements LLMClient {

    private final OpenAIClient client;
    private final String model;

    public SimpleOpenAIClientImpl(OpenAIClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String generate(String prompt) {
        Response response = client.responses().create(
                ResponseCreateParams.builder()
                        .model(ChatModel.of(model))
                        .input(prompt)
                        .build()
        );

        return response.output().stream()
                .flatMap(o -> o.message().stream())
                .flatMap(m -> m.content().stream())
                .flatMap(c -> c.outputText().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("");
    }
}
