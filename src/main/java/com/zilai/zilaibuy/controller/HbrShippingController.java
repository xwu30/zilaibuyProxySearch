package com.zilai.zilaibuy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class HbrShippingController {

    // Credentials for fee-query API
    @Value("${hbr.app-token}")
    private String appToken;

    @Value("${hbr.app-key}")
    private String appKey;

    @Value("${hbr.url}")
    private String hbrUrl;

    // Credentials for order-tracking API (consolidatedorderstatus)
    @Value("${hbr.tracking-app-token:609afb949bce1654940aa8f402b65dbf}")
    private String trackingAppToken;

    @Value("${hbr.tracking-app-key:9cd9ffad68766e637047b973facac4a59cd9ffad68766e637047b973facac4a5}")
    private String trackingAppKey;

    private final ForwardingParcelRepository parcelRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Temporary debug endpoint — no auth, returns raw HBR response for diagnostics */
    @GetMapping("/debug-quote")
    public ResponseEntity<String> debugQuote() {
        try {
            String paramsJson = "{\"country_code\":\"CA\",\"weight\":2.000}";
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
            return ResponseEntity.ok("STATUS:" + response.statusCode() + " BODY:" + response.body());
        } catch (Exception e) {
            return ResponseEntity.ok("ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @GetMapping("/quote")
    public ResponseEntity<?> getQuote(
            @RequestParam double weightKg,
            @RequestParam(defaultValue = "CA") String countryCode) {
        try {
            String country = countryCode;
            String paramsJson = String.format("{\"country_code\":\"%s\",\"weight\":%.3f}", country, weightKg);

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

    /**
     * Admin-only: query HBR for the latest status of a parcel's inbound tracking number,
     * then update our local parcel record accordingly.
     *
     * POST /api/shipping/sync-parcel/{parcelId}
     * Response: { status, hbrStatus, message }
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sync-parcel/{parcelId}")
    public ResponseEntity<Map<String, Object>> syncParcelStatus(@PathVariable Long parcelId) {
        ForwardingParcelEntity parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new RuntimeException("包裹不存在"));

        String trackingNo = parcel.getInboundTrackingNo();
        if (trackingNo == null || trackingNo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该包裹无快递单号"));
        }

        try {
            String paramsJson = String.format("{\"order_tracking_number\":\"%s\"}", trackingNo.trim());
            String body = "appToken=" + encode(trackingAppToken)
                    + "&appKey=" + encode(trackingAppKey)
                    + "&serviceMethod=consolidatedorderstatus"
                    + "&paramsJson=" + encode(paramsJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(hbrUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("HBR sync parcel {} ({}): HTTP {} body={}", parcelId, trackingNo, response.statusCode(), response.body());

            JsonNode root = mapper.readTree(response.body());
            if (!root.has("success") || root.get("success").asInt() != 1) {
                String msg = root.has("msg") ? root.get("msg").asText() : "HBR 返回失败";
                return ResponseEntity.ok(Map.of("message", msg));
            }

            // Parse status from response data
            JsonNode data = root.path("data");
            String hbrStatus = null;
            if (data.isArray() && data.size() > 0) {
                hbrStatus = data.get(0).path("status").asText(null);
                if (hbrStatus == null) hbrStatus = data.get(0).path("Status").asText(null);
            } else if (data.isObject()) {
                hbrStatus = data.path("status").asText(null);
                if (hbrStatus == null) hbrStatus = data.path("Status").asText(null);
            }

            ParcelStatus newStatus = mapHbrStatus(hbrStatus);
            String oldStatus = parcel.getStatus().name();

            if (newStatus != null && newStatus.ordinal() > parcel.getStatus().ordinal()) {
                parcel.setStatus(newStatus);
                if (newStatus == ParcelStatus.IN_WAREHOUSE && parcel.getCheckinDate() == null) {
                    parcel.setCheckinDate(LocalDateTime.now());
                }
                parcelRepository.save(parcel);
                log.info("Parcel {} status updated {} → {} via HBR query", parcelId, oldStatus, newStatus);
            }

            return ResponseEntity.ok(Map.of(
                    "hbrStatus", hbrStatus != null ? hbrStatus : "unknown",
                    "status", parcel.getStatus().name(),
                    "updated", newStatus != null && !oldStatus.equals(parcel.getStatus().name())
            ));

        } catch (Exception e) {
            log.error("Failed to sync parcel {} from HBR", parcelId, e);
            return ResponseEntity.status(500).body(Map.of("message", "查询失败: " + e.getMessage()));
        }
    }

    private ParcelStatus mapHbrStatus(String s) {
        if (s == null) return null;
        return switch (s.toUpperCase().trim()) {
            case "RECEIVED"  -> ParcelStatus.IN_WAREHOUSE;   // 包裹签入
            case "PACKED"    -> ParcelStatus.PACKING;         // 运单打包完成
            case "SHIPPED"   -> ParcelStatus.SHIPPED;         // 运单发货
            case "DELIVERED" -> ParcelStatus.DELIVERED;       // 运单签收
            default          -> null;
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
