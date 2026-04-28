
package me.asu.ai.llm;

import java.net.http.*;
import java.net.URI;

public class OllamaClientImpl implements LLMClient {

    private final String model;
    private final String baseUrl;

    public OllamaClientImpl(String model, String baseUrl) {
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public String generate(String prompt) {

        try {
            HttpClient client = HttpClient.newHttpClient();

            String body = """
            {
              "model": "%s",
              "prompt": "%s",
              "stream": false
            }
            """.formatted(model, escape(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/api/generate"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            // 简化解析
            String resp = response.body();

            return extractResponse(resp);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractResponse(String json) {
        int idx = json.indexOf("\"response\":\"");
        if (idx < 0) return "";

        String sub = json.substring(idx + 12);
        return sub.substring(0, sub.indexOf("\""));
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private String normalizeBaseUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
