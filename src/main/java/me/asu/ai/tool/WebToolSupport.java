package me.asu.ai.tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebToolSupport {

    private static final Pattern SEARCH_RESULT_PATTERN = Pattern.compile(
            "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient;

    public WebToolSupport() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public WebToolSupport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String fetch(String url, int maxChars) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ai-fix/1.0")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body() == null ? "" : response.body();
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "\n...(truncated)";
    }

    public String search(String query, String site, int limit) throws IOException, InterruptedException {
        String effectiveQuery = query == null ? "" : query.trim();
        if (effectiveQuery.isBlank()) {
            return "No search query provided.";
        }
        if (site != null && !site.isBlank()) {
            effectiveQuery = effectiveQuery + " site:" + site.trim();
        }
        String encoded = URLEncoder.encode(effectiveQuery, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encoded;
        String html = fetch(url, 40_000);
        List<String> lines = parseSearchResults(html, limit);
        if (lines.isEmpty()) {
            return "No search results found for query: " + effectiveQuery;
        }
        return String.join("\n", lines);
    }

    List<String> parseSearchResults(String html, int limit) {
        Matcher matcher = SEARCH_RESULT_PATTERN.matcher(html == null ? "" : html);
        List<String> lines = new ArrayList<>();
        int index = 1;
        while (matcher.find() && index <= Math.max(1, limit)) {
            String href = unescapeHtml(matcher.group(1));
            String title = sanitizeHtml(matcher.group(2));
            lines.add(index + ". " + title + " | " + href);
            index++;
        }
        return lines;
    }

    private String sanitizeHtml(String value) {
        return unescapeHtml(HTML_TAG_PATTERN.matcher(value == null ? "" : value).replaceAll(" "))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String unescapeHtml(String value) {
        return (value == null ? "" : value)
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
