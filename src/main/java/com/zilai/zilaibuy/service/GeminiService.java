package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";

    public GeminiService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.baseUrl(GEMINI_BASE).build();
        this.objectMapper = objectMapper;
    }

    /** Fetch available models that support generateContent */
    private List<String> listAvailableModels() {
        try {
            String response = webClient.get()
                    .uri("/models?key={key}", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            List<String> models = new ArrayList<>();
            for (JsonNode m : root.path("models")) {
                String name = m.path("name").asText(); // e.g. "models/gemini-1.5-flash"
                // Check if it supports generateContent
                for (JsonNode method : m.path("supportedGenerationMethods")) {
                    if ("generateContent".equals(method.asText())) {
                        // Strip "models/" prefix
                        models.add(name.replace("models/", ""));
                        break;
                    }
                }
            }
            return models;
        } catch (Exception e) {
            return List.of("gemini-1.5-flash", "gemini-1.5-pro", "gemini-pro");
        }
    }

    public String parseProductUrl(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Gemini API key not configured on server.");
        }

        List<String> models = listAvailableModels();
        if (models.isEmpty()) {
            throw new RuntimeException("No Gemini models available for this API key.");
        }

        String prompt = """
                分析此商品链接的信息: %s
                商品名称（title）必须保留原始日文，不要翻译。
                description 和 material 请翻译为简体中文。
                判断商品是否包含液体、电池或易燃物，若有请将 isRestricted 设为 true 并说明原因。
                严格返回如下 JSON 格式，不要包含任何额外文字：
                {
                  "title": "商品原始日文名称（保留日文）",
                  "priceJpy": 价格数字（日元整数）,
                  "priceCny": 价格数字（人民币，按汇率0.048换算）,
                  "exchangeRate": 0.048,
                  "imageUrl": "商品图片URL",
                  "description": "商品描述（中文，100字以内）",
                  "platform": "平台名称如Amazon/Mercari",
                  "isRestricted": false,
                  "restrictionReason": "",
                  "material": "材质（中文）"
                }
                """.formatted(url);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        Exception lastError = null;
        for (String model : models) {
            // Prefer flash/lite models for speed and availability
            if (!model.contains("flash") && !model.contains("lite") && !model.contains("pro")) continue;

            try {
                String responseBody = webClient.post()
                        .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(responseBody);

                // Check for API error in response body
                if (root.has("error")) {
                    throw new RuntimeException(root.at("/error/message").asText());
                }

                String text = root.at("/candidates/0/content/parts/0/text").asText();
                if (text == null || text.isBlank()) throw new RuntimeException("Empty response from model " + model);
                return text;

            } catch (Exception e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (!msg.contains("404") && !msg.contains("NOT_FOUND") && !msg.contains("not found")
                        && !msg.contains("no longer available") && !msg.contains("not supported")) {
                    break;
                }
            }
        }

        // Try any remaining model without filter
        for (String model : models) {
            try {
                String responseBody = webClient.post()
                        .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("error")) continue;

                String text = root.at("/candidates/0/content/parts/0/text").asText();
                if (text != null && !text.isBlank()) return text;
            } catch (Exception e) {
                lastError = e;
            }
        }

        throw new RuntimeException("解析失败，可用模型: " + models + "。最后错误: " +
                (lastError != null ? lastError.getMessage() : "unknown"));
    }
}
