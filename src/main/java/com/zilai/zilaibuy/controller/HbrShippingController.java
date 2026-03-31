package com.zilai.zilaibuy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/shipping")
public class HbrShippingController {

    @Value("${hbr.app-token}")
    private String appToken;

    @Value("${hbr.app-key}")
    private String appKey;

    @Value("${hbr.url}")
    private String hbrUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    record QuoteRequest(double weightKg, String countryCode) {}

    @PostMapping("/quote")
    public ResponseEntity<?> getQuote(@RequestBody QuoteRequest req) {
        try {
            String country = req.countryCode() != null ? req.countryCode() : "CA";
            String paramsJson = String.format("{\"country_code\":\"%s\",\"weight\":%.3f}", country, req.weightKg());

            String body = "appToken=" + encode(appToken)
                    + "&appKey=" + encode(appKey)
                    + "&serviceMethod=feetrail"
                    + "&paramsJson=" + encode(paramsJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(hbrUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = mapper.readTree(response.body());

            if (!root.has("success") || root.get("success").asInt() != 1) {
                return ResponseEntity.ok(List.of());
            }

            JsonNode data = root.get("data");
            List<Object> lines = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    lines.add(mapper.convertValue(item, Object.class));
                }
            }

            return ResponseEntity.ok(lines);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("运费查询失败: " + e.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
