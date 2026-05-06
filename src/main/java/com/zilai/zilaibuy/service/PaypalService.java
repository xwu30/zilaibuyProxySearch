package com.zilai.zilaibuy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaypalService {

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.mode:sandbox}")
    private String mode;

    private final RestTemplate restTemplate;

    private String baseUrl() {
        return "sandbox".equals(mode)
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";
    }

    public String getAccessToken() {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + credentials);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/v1/oauth2/token",
                new HttpEntity<>(body, headers),
                Map.class);
        return (String) response.getBody().get("access_token");
    }

    /** Creates a PayPal order and returns the PayPal order ID. */
    public String createOrder(long amountJpy, String description, String customId) {
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("amount", Map.of("currency_code", "JPY", "value", String.valueOf(amountJpy)));
        unit.put("description", description.length() > 127 ? description.substring(0, 127) : description);
        if (customId != null) unit.put("custom_id", customId);

        Map<String, Object> orderBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", new Object[]{unit}
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/v2/checkout/orders",
                new HttpEntity<>(orderBody, headers),
                Map.class);

        String orderId = (String) response.getBody().get("id");
        log.info("PayPal order created: {} ({})", orderId, customId);
        return orderId;
    }

    /** Captures a PayPal order. Returns the full response body. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> captureOrder(String paypalOrderId) {
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl() + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                    new HttpEntity<>(headers),
                    Map.class);
            log.info("PayPal capture response for {}: status={}", paypalOrderId,
                    response.getBody() != null ? response.getBody().get("status") : "null");
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("PayPal capture error for {}: {} {}", paypalOrderId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayPal支付捕获失败: " + e.getMessage());
        }
    }

    public boolean isCompleted(Map<String, Object> captureResult) {
        return captureResult != null && "COMPLETED".equals(captureResult.get("status"));
    }
}
