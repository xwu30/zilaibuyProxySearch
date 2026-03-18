package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/parse-product")
@RequiredArgsConstructor
public class ParseProductController {

    private final GeminiService geminiService;

    @PostMapping
    public ResponseEntity<?> parseProduct(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        try {
            String json = geminiService.parseProductUrl(url);
            // Return the raw JSON string as a JSON response
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(json);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
