package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODELS = {
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro"
    };

    public GeminiService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.baseUrl(GEMINI_BASE).build();
        this.objectMapper = objectMapper;
    }

    public String parseProductUrl(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Gemini API key not configured on server.");
        }

        String prompt = """
                分析此商品链接的信息: %s
                请将所有日文内容翻译为简体中文。
                判断商品是否包含液体、电池或易燃物，若有请将 isRestricted 设为 true 并说明原因。
                严格返回如下 JSON 格式，不要包含任何额外文字：
                {
                  "title": "商品名称（中文）",
                  "priceJpy": 价格数字（日元）,
                  "priceCny": 价格数字（人民币，按汇率0.048换算）,
                  "exchangeRate": 汇率数字,
                  "imageUrl": "商品图片URL",
                  "description": "商品描述（中文）",
                  "platform": "平台名称",
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
        for (String model : MODELS) {
            try {
                String responseBody = webClient.post()
                        .uri("/{model}:generateContent?key={key}", model, apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(responseBody);
                String text = root.at("/candidates/0/content/parts/0/text").asText();
                if (text == null || text.isBlank()) throw new RuntimeException("Empty response from model");
                return text;

            } catch (Exception e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                // Only retry on model-not-found errors
                if (!msg.contains("404") && !msg.contains("NOT_FOUND") && !msg.contains("not found")) {
                    break;
                }
            }
        }

        throw new RuntimeException(lastError != null ? lastError.getMessage() : "All Gemini models failed");
    }
}
