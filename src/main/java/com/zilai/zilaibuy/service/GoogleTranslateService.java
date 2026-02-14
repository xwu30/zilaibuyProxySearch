package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Service
public class GoogleTranslateService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTranslateService.class);
    private static final String TRANSLATE_URL = "https://translation.googleapis.com/language/translate/v2";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GoogleTranslateService(WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper,
                                  @Value("${google.translate.api-key}") String apiKey) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return text;
        List<String> results = translateBatch(List.of(text), sourceLang, targetLang);
        return results.isEmpty() ? text : results.get(0);
    }

    public List<String> translateBatch(List<String> texts, String sourceLang, String targetLang) {
        if (texts == null || texts.isEmpty()) return List.of();

        // Filter out null/blank entries but remember their positions
        List<Integer> nonBlankIndices = new ArrayList<>();
        List<String> nonBlankTexts = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i) != null && !texts.get(i).isBlank()) {
                nonBlankIndices.add(i);
                nonBlankTexts.add(texts.get(i));
            }
        }

        if (nonBlankTexts.isEmpty()) return new ArrayList<>(texts);

        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(TRANSLATE_URL)
                    .queryParam("key", apiKey)
                    .queryParam("source", sourceLang)
                    .queryParam("target", targetLang)
                    .queryParam("format", "text");
            for (String t : nonBlankTexts) {
                uriBuilder.queryParam("q", t);
            }
            URI uri = uriBuilder.build().toUri();

            String responseBody = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode translations = root.path("data").path("translations");

            // Build result array preserving original positions
            List<String> results = new ArrayList<>(texts);
            for (int i = 0; i < translations.size() && i < nonBlankIndices.size(); i++) {
                String translated = translations.get(i).path("translatedText").asText();
                results.set(nonBlankIndices.get(i), translated);
            }
            return results;

        } catch (Exception e) {
            log.error("Google Translate API call failed: {}", e.getMessage(), e);
            return new ArrayList<>(texts);
        }
    }
}
