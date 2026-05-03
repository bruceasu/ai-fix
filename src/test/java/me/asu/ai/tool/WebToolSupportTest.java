package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebToolSupportTest {

    @Test
    void shouldParseDuckDuckGoHtmlResults() {
        WebToolSupport support = new WebToolSupport();
        String html = """
                <html><body>
                <a class="result__a" href="https://docs.example.com/a">Spring Retry Guide</a>
                <a class="result__a" href="https://docs.example.com/b">Java Error Handling</a>
                </body></html>
                """;

        List<String> results = support.parseSearchResults(html, 2);

        assertEquals(2, results.size());
        assertEquals("1. Spring Retry Guide | https://docs.example.com/a", results.get(0));
        assertEquals("2. Java Error Handling | https://docs.example.com/b", results.get(1));
    }
}
