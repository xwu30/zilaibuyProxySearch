package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.OrderItemEntity;
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
import java.util.List;

@Slf4j
@Service
public class HbrService {

    @Value("${hbr.app-token}")
    private String appToken;

    @Value("${hbr.app-key}")
    private String appKey;

    @Value("${hbr.url}")
    private String hbrUrl;

    @Value("${hbr.user-id}")
    private String hbrUserId;

    @Value("${hbr.user-code}")
    private String hbrUserCode;

    @Value("${hbr.user-name}")
    private String hbrUserName;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Calls HBR createconsolidatedorder API synchronously with full invoice data.
     * Returns null on success, or an error message string on failure.
     */
    public String createConsolidatedOrder(ForwardingParcelEntity parcel) {
        String trackingNo = parcel.getInboundTrackingNo();
        if (trackingNo == null || trackingNo.isBlank()) return null;
        try {
            String trackingType = mapCarrierToTrackingType(parcel.getCarrier());
            String cnName = parcel.getContent() != null ? parcel.getContent() : "商品";
            String enName = isEnglish(cnName) ? cnName : "Goods";
            int quantity = 1;
            int unitCharge = parcel.getDeclaredValue() != null ? parcel.getDeclaredValue().intValue() : 1;

            com.zilai.zilaibuy.entity.UserEntity user = parcel.getUser();
            String userId = user.getCloudId() != null && !user.getCloudId().isBlank()
                    ? user.getCloudId()
                    : String.valueOf(user.getId());
            String userCode = userId;
            String userName = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername()
                    : (user.getDisplayName() != null && !user.getDisplayName().isBlank()
                        ? user.getDisplayName()
                        : userId);

            String paramsJson = mapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                put("order_tracking_number", trackingNo.trim());
                put("tracking_type", trackingType);
                put("user_id", userId);
                put("user_code", userCode);
                put("user_name", userName);
                put("invoice", java.util.List.of(new java.util.LinkedHashMap<String, Object>() {{
                    put("invoice_enname", enName);
                    put("invoice_cnname", cnName);
                    put("invoice_quantity", quantity);
                    put("unit_code", "PCE");
                    put("invoice_unitcharge", unitCharge);
                    put("invoice_note", cnName);
                }}));
                put("extra_service", java.util.List.of());
            }});

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
                log.info("HBR createconsolidatedorder OK for tracking={}", trackingNo);
                return null;
            } else {
                String msg = root.has("cnmessage") ? root.get("cnmessage").asText() : response.body();
                log.warn("HBR createconsolidatedorder failed for tracking={}: {}", trackingNo, msg);
                return msg;
            }
        } catch (Exception e) {
            log.error("HBR createconsolidatedorder error for tracking={}: {}", trackingNo, e.getMessage());
            return "HBR连接失败: " + e.getMessage();
        }
    }

    /**
     * Called when admin fills in item tracking number.
     * Returns null on success, or an error message string on failure.
     */
    public String createConsolidatedOrderForItem(String trackingNo, String carrier, OrderItemEntity item) {
        try {
            String trackingType = mapCarrierToTrackingType(carrier);
            String cnName = item.getProductTitle() != null ? item.getProductTitle() : "商品";
            String enName = isEnglish(cnName) ? cnName : "Goods";
            int quantity = item.getQuantity();
            int unitCharge = item.getPriceJpy() > 0 ? item.getPriceJpy() : 1;

            com.zilai.zilaibuy.entity.UserEntity user = item.getOrder().getUser();
            String userId = user.getCloudId() != null && !user.getCloudId().isBlank()
                    ? user.getCloudId()
                    : String.valueOf(user.getId());
            String userCode = userId;
            String userName = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername()
                    : (user.getDisplayName() != null && !user.getDisplayName().isBlank()
                        ? user.getDisplayName()
                        : userId);

            String paramsJson = mapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                put("order_tracking_number", trackingNo);
                put("tracking_type", trackingType);
                put("user_id", userId);
                put("user_code", userCode);
                put("user_name", userName);
                put("invoice", java.util.List.of(new java.util.LinkedHashMap<String, Object>() {{
                    put("invoice_enname", enName);
                    put("invoice_cnname", cnName);
                    put("invoice_quantity", quantity);
                    put("unit_code", "PCE");
                    put("invoice_unitcharge", unitCharge);
                    put("invoice_note", enName);
                }}));
                put("extra_service", java.util.List.of());
            }});

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
                log.info("HBR createconsolidatedorder OK for item tracking={}", trackingNo);
                return null;
            } else {
                String msg = root.has("cnmessage") ? root.get("cnmessage").asText() : response.body();
                log.warn("HBR createconsolidatedorder failed for item tracking={}: {}", trackingNo, msg);
                return msg;
            }
        } catch (Exception e) {
            log.error("HBR createconsolidatedorder error for item tracking={}: {}", trackingNo, e.getMessage());
            return "HBR连接失败: " + e.getMessage();
        }
    }

    /**
     * Calls HBR createconsolidatedshipment to create a consolidated shipping order.
     * @param trackingNumbers list of inbound tracking numbers to include
     * @param serviceCode     HBR service/line code (e.g. "CA-EMS")
     * @param user            the customer user entity (for user_id/user_code/user_name params)
     * @return the HBR order_Id string on success, or null on failure
     */
    public String createConsolidatedShipment(java.util.List<String> trackingNumbers, String serviceCode,
                                              com.zilai.zilaibuy.entity.UserEntity user) {
        try {
            String userId = (user != null && user.getCloudId() != null && !user.getCloudId().isBlank())
                    ? user.getCloudId() : (user != null ? String.valueOf(user.getId()) : hbrUserId);
            String userCode = userId;
            String userName = (user != null && user.getUsername() != null && !user.getUsername().isBlank())
                    ? user.getUsername()
                    : (user != null && user.getDisplayName() != null && !user.getDisplayName().isBlank()
                        ? user.getDisplayName() : userId);

            java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
            // HBR expects singular key "order_tracking_number" with array value
            params.put("order_tracking_number", trackingNumbers);
            params.put("user_id", userId);
            params.put("user_code", userCode);
            params.put("user_name", userName);
            if (serviceCode != null && !serviceCode.isBlank()) {
                params.put("service_code", serviceCode);
            }
            String paramsJson = mapper.writeValueAsString(params);

            String body = "appToken=" + encode(appToken)
                    + "&appKey=" + encode(appKey)
                    + "&serviceMethod=createconsolidatedshipment"
                    + "&paramsJson=" + encode(paramsJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(hbrUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            log.info("HBR createconsolidatedshipment response: {}", response.body());

            if (root.has("success") && root.get("success").asInt() == 1) {
                JsonNode orderId = root.get("order_Id");
                if (orderId == null) orderId = root.get("order_id");
                if (orderId != null && !orderId.isNull()) {
                    return orderId.asText();
                }
                // Try nested data
                JsonNode data = root.get("data");
                if (data != null) {
                    JsonNode nestedId = data.get("order_Id");
                    if (nestedId == null) nestedId = data.get("order_id");
                    if (nestedId != null && !nestedId.isNull()) return nestedId.asText();
                }
                log.warn("HBR createconsolidatedshipment succeeded but no order_Id in response: {}", response.body());
                return null;
            } else {
                String msg = root.has("cnmessage") ? root.get("cnmessage").asText() : response.body();
                log.warn("HBR createconsolidatedshipment failed: {}", msg);
                return null;
            }
        } catch (Exception e) {
            log.error("HBR createconsolidatedshipment error: {}", e.getMessage());
            return null;
        }
    }

    /** Returns true if the string contains only ASCII/Latin characters (no CJK). */
    private boolean isEnglish(String s) {
        return s != null && s.chars().allMatch(c -> c < 0x3000);
    }

    private String mapCarrierToTrackingType(String carrier) {
        if (carrier == null) return "other";
        String lower = carrier.toLowerCase();
        if (lower.contains("japan-post") || lower.contains("jp post") || lower.contains("日本邮政")) return "japan-post";
        if (lower.contains("seino") || lower.contains("西浓")) return "seino";
        if (lower.contains("yamato") || lower.contains("雅玛多") || lower.contains("黑猫")) return "yamato";
        if (lower.contains("sagawa") || lower.contains("佐川")) return "sagawa";
        return "other";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
