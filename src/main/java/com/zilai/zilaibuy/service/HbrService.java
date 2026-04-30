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

    public record HbrResult(String packingNo, String errorMessage) {
        public boolean success() { return packingNo != null && !packingNo.isBlank(); }
    }

    /**
     * Calls HBR createconsolidatedshipment to create a consolidated shipping order.
     * @param trackingNumbers list of inbound tracking numbers to include
     * @param serviceCode     HBR service/line code (e.g. "CA-EMS")
     * @param user            the customer user entity (for user_id/user_code/user_name params)
     * @return HbrResult with packingNo on success, or errorMessage on failure
     */
    public HbrResult createConsolidatedShipment(java.util.List<String> trackingNumbers, String serviceCode,
                                              com.zilai.zilaibuy.entity.UserEntity user,  // kept for API compat
                                              java.util.Map<String, String> addr) {
        try {
            java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("order_tracking_number", trackingNumbers);
            if (serviceCode != null && !serviceCode.isBlank()) {
                params.put("shipping_method", serviceCode);
            }
            // Consignee (receiver) info — correct HBR field names confirmed via Postman
            if (addr != null) {
                java.util.Map<String, Object> consignee = new java.util.LinkedHashMap<>();
                consignee.put("consignee_name",        addr.getOrDefault("fullName", ""));
                consignee.put("consignee_company",     "");
                consignee.put("consignee_countrycode", addr.getOrDefault("country", ""));
                consignee.put("consignee_province",    addr.getOrDefault("province", ""));
                consignee.put("consignee_city",        addr.getOrDefault("city", ""));
                consignee.put("consignee_street",      addr.getOrDefault("street", ""));
                consignee.put("consignee_postcode",    addr.getOrDefault("postalCode", ""));
                consignee.put("consignee_telephone",   addr.getOrDefault("phone", ""));
                consignee.put("consignee_mobile",      addr.getOrDefault("phone", ""));
                params.put("consignee", consignee);
            }
            String paramsJson = mapper.writeValueAsString(params);
            log.info("HBR createconsolidatedshipment request paramsJson: {}", paramsJson);

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
                // Prefer refrence_no (HBT-prefixed) from data object
                JsonNode data = root.get("data");
                if (data != null) {
                    JsonNode refNo = data.get("refrence_no");
                    if (refNo != null && !refNo.isNull() && !refNo.asText().isBlank()) {
                        return new HbrResult(refNo.asText(), null);
                    }
                    JsonNode nestedId = data.get("order_Id");
                    if (nestedId == null) nestedId = data.get("order_id");
                    if (nestedId != null && !nestedId.isNull()) return new HbrResult(nestedId.asText(), null);
                }
                // Fallback to top-level order_id
                JsonNode orderId = root.get("order_Id");
                if (orderId == null) orderId = root.get("order_id");
                if (orderId != null && !orderId.isNull() && orderId.asLong() > 0) {
                    return new HbrResult(orderId.asText(), null);
                }
                log.warn("HBR createconsolidatedshipment succeeded but no order_Id in response: {}", response.body());
                return new HbrResult(null, "HBR 创单成功但未返回单号，请联系客服");
            } else {
                String msg = root.has("cnmessage") ? root.get("cnmessage").asText() : response.body();
                log.warn("HBR createconsolidatedshipment failed: {}", msg);
                return new HbrResult(null, msg);
            }
        } catch (Exception e) {
            log.error("HBR createconsolidatedshipment error: {}", e.getMessage());
            return new HbrResult(null, "HBR 请求异常：" + e.getMessage());
        }
    }

    /**
     * Pushes payment confirmation to HBR after shipping fee is paid.
     * serviceMethod: pushorderpayinfo
     *
     * @param hawbCode    HBT parcel number (order.packingNo)
     * @param totalJpy    total amount paid in JPY (all fees combined)
     * @param payCode     payment reference (Stripe PaymentIntent ID or internal ID)
     * @param payInfo     additional account info (username/phone)
     * @param payType     "Stripe" or "Balance"
     * @param orderNo     order number for remark
     */
    public void pushOrderPayInfo(String hawbCode, long totalJpy, String payCode,
                                  String payInfo, String payType, String orderNo) {
        if (hawbCode == null || hawbCode.isBlank()) {
            log.warn("pushOrderPayInfo skipped: no HBT hawbCode for order {}", orderNo);
            return;
        }
        try {
            String payDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());
            String payFee = String.format("%.2f", (double) totalJpy);

            java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("server_hawbcode", hawbCode);
            params.put("pay_fee", payFee);
            params.put("pay_date", payDate);
            params.put("pay_code", payCode != null ? payCode : orderNo);
            params.put("pay_info", payInfo != null ? payInfo : "");
            params.put("pay_type", payType != null ? payType : "Stripe");
            params.put("pay_currency", "JPY");
            params.put("remark", orderNo);

            String paramsJson = mapper.writeValueAsString(params);

            String body = "appToken=" + encode(appToken)
                    + "&appKey=" + encode(appKey)
                    + "&serviceMethod=pushorderpayinfo"
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
                log.info("HBR pushorderpayinfo OK for hawbCode={}, order={}", hawbCode, orderNo);
            } else {
                String msg = root.has("cnmessage") ? root.get("cnmessage").asText() : response.body();
                log.warn("HBR pushorderpayinfo failed for hawbCode={}, order={}: {}", hawbCode, orderNo, msg);
            }
        } catch (Exception e) {
            log.error("HBR pushorderpayinfo error for hawbCode={}, order={}: {}", hawbCode, orderNo, e.getMessage());
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

    private void putIfPresent(java.util.Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
