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
            // Fetch og:title and og:image from the real page HTML
            PageMeta meta = fetchPageMeta(url);

            String json = geminiService.parseProductUrl(url);
            JsonNode node = objectMapper.readTree(json);

            if (node instanceof ObjectNode obj) {
                // Always use the real og:title (Japanese) over Gemini's translation
                if (meta.title != null && !meta.title.isBlank()) {
                    obj.put("title", meta.title);
                }
                // Use real og:image; wrap non-Amazon images through proxy to bypass hotlink protection
                if (meta.imageUrl != null) {
                    String cleaned = cleanAmazonImageUrl(meta.imageUrl);
                    String proxied = needsProxy(cleaned) ? "/api/image-proxy?url=" + java.net.URLEncoder.encode(cleaned, "UTF-8") : cleaned;
                    obj.put("imageUrl", proxied);
                }
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private boolean needsProxy(String imageUrl) {
        if (imageUrl == null) return false;
        return !imageUrl.contains("amazon.com") && !imageUrl.contains("media-amazon.com")
                && !imageUrl.contains("rakuten.co.jp") && !imageUrl.contains("r10s.jp");
    }

    /** Strip Amazon image size modifiers, e.g. ._SY466_ ._SL1500_ ._AC_SX425_ */
    private String cleanAmazonImageUrl(String url) {
        if (url == null) return null;
        if (url.contains("amazon.com") || url.contains("media-amazon.com")) {
            // e.g. 51p8U8eYlwL._SY466_.jpg -> 51p8U8eYlwL.jpg
            // e.g. 51wB6Q+s7zL._SY498_B01,204,203,200_.jpg -> 51wB6Q+s7zL.jpg
            return url.replaceAll("\\._[A-Za-z0-9,_]+(?=\\.[a-z]+$)", "");
        }
        return url;
    }

    record PageMeta(String title, String imageUrl) {}

    private PageMeta fetchPageMeta(String pageUrl) {
        String title = null;
        String imageUrl = null;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(pageUrl).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "ja,en;q=0.9");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);

            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[65536];
                int read = is.read(buf);
                String html = new String(buf, 0, read > 0 ? read : 0, "UTF-8");

                // og:title — property before content
                Pattern p1 = Pattern.compile("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                Matcher m = p1.matcher(html);
                if (m.find()) title = decodeHtmlEntities(m.group(1));
                // og:title — content before property
                if (title == null) {
                    Pattern p2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:title[\"']", Pattern.CASE_INSENSITIVE);
                    m = p2.matcher(html);
                    if (m.find()) title = decodeHtmlEntities(m.group(1));
                }
                // <title> tag fallback
                if (title == null) {
                    Pattern p3 = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
                    m = p3.matcher(html);
                    if (m.find()) title = decodeHtmlEntities(m.group(1).trim());
                }

                // og:image — property before content
                Pattern pi1 = Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                m = pi1.matcher(html);
                if (m.find()) imageUrl = m.group(1);
                // og:image — content before property
                if (imageUrl == null) {
                    Pattern pi2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);
                    m = pi2.matcher(html);
                    if (m.find()) imageUrl = m.group(1);
                }
            }
        } catch (Exception e) {
            // Fall through — Gemini values used as fallback
        }
        return new PageMeta(title, imageUrl);
    }

    private String decodeHtmlEntities(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#039;", "'").replace("&nbsp;", " ");
    }
}
