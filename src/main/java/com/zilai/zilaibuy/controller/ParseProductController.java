package com.zilai.zilaibuy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zilai.zilaibuy.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/parse-product")
@RequiredArgsConstructor
public class ParseProductController {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> parseProduct(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        try {
            // Fetch real image URL from OG tags before calling AI
            String realImageUrl = fetchOgImage(url);

            String json = geminiService.parseProductUrl(url);
            JsonNode node = objectMapper.readTree(json);

            // Override Gemini's hallucinated imageUrl with the real one
            if (realImageUrl != null && node instanceof ObjectNode obj) {
                obj.put("imageUrl", realImageUrl);
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private String fetchOgImage(String pageUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(pageUrl).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "ja,en;q=0.9");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);

            try (InputStream is = conn.getInputStream()) {
                // Read only first 50KB — OG tags are always in <head>
                byte[] buf = new byte[51200];
                int read = is.read(buf);
                String html = new String(buf, 0, read > 0 ? read : 0, "UTF-8");

                // Try og:image first
                Pattern ogImage = Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                Matcher m = ogImage.matcher(html);
                if (m.find()) return m.group(1);

                // Fallback: content before property
                Pattern ogImage2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);
                m = ogImage2.matcher(html);
                if (m.find()) return m.group(1);
            }
        } catch (Exception e) {
            // Silently fall through — Gemini's imageUrl will be used as fallback
        }
        return null;
    }
}
