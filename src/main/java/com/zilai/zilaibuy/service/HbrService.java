package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
public class HbrService {

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

    /**
     * Calls HBR createconsolidatedorder API synchronously.
     * Returns null on success, or an error message string on failure.
     */
    public String createConsolidatedOrder(String orderTrackingNumber) {
        if (orderTrackingNumber == null || orderTrackingNumber.isBlank()) return null;
        try {
            String paramsJson = "{\"order_tracking_number\":\"" + orderTrackingNumber.trim() + "\"}";
            String body = "appToken=" + encode(appToken)
                    + "&appKey=" + encode(appKey)
                    + "&serviceMethod=createconsolidatedorder"
                    + "&paramsJson=" + encode(paramsJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(hbrUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            if (root.has("success") && root.get("success").asInt() == 1) {
                log.info("HBR createconsolidatedorder OK for tracking={}", orderTrackingNumber);
                return null;
            } else {
                String msg = root.has("cnmessage") ? root.get("cnmessage").asText() : response.body();
                log.warn("HBR createconsolidatedorder failed for tracking={}: {}", orderTrackingNumber, msg);
                return msg;
            }
        } catch (Exception e) {
            log.error("HBR createconsolidatedorder error for tracking={}: {}", orderTrackingNumber, e.getMessage());
            return "HBR连接失败: " + e.getMessage();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
