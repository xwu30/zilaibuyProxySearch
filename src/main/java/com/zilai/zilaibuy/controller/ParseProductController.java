package com.zilai.zilaibuy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zilai.zilaibuy.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/parse-product")
@RequiredArgsConstructor
public class ParseProductController {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url:https://api.zilaibuy.com}")
    private String baseUrl;

    @PostMapping
    public ResponseEntity<?> parseProduct(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        try {
            String json = geminiService.parseProductUrl(url);
            // Rewrite imageUrl to go through our proxy
            JsonNode node = objectMapper.readTree(json);
            String imageUrl = node.path("imageUrl").asText();
            if (imageUrl != null && !imageUrl.isBlank() && node instanceof ObjectNode obj) {
                String proxied = baseUrl + "/api/image-proxy?url=" + URLEncoder.encode(imageUrl, StandardCharsets.UTF_8);
                obj.put("imageUrl", proxied);
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
