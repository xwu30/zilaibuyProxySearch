package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.OrderEntity.OrderStatus;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import com.zilai.zilaibuy.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Receives status push callbacks from HBR (海必达) for:
 *   1. 预报包裹 — individual parcels forecasted into our HBR warehouse account
 *   2. 合箱打包 — consolidated shipments (multiple parcels packed & dispatched together)
 *
 * Security: HBR includes appToken in every callback body; we validate it against
 * our configured hbr.app-token. Both endpoints are public (no JWT) so HBR can call
 * them without a user session, matching the pattern used by /api/payments/webhook.
 *
 * HBR status codes (中文 or uppercase English accepted):
 *   Inbound:  RECEIVED / 已收货 / IN_WAREHOUSE / 入库 → IN_WAREHOUSE
 *             PACKING / 打包中                         → PACKING
 *             SHIPPED / 已发货                         → SHIPPED
 *             DELIVERED / 已签收                       → DELIVERED
 *   Shipment: PACKING / 打包完成                       → PACKING  (order + parcels)
 *             SHIPPED / 已发货                         → SHIPPED  (sets outbound tracking)
 *             DELIVERED / 已签收                       → DELIVERED
 */
@Slf4j
@RestController
@RequestMapping("/api/hbr/callback")
@RequiredArgsConstructor
public class HbrCallbackController {

    private static final java.math.BigDecimal JPY_TO_CNY = new java.math.BigDecimal("0.0467");

    private final ForwardingParcelRepository parcelRepository;
    private final OrderRepository orderRepository;

    @Value("${hbr.app-token}")
    private String configuredAppToken;

    // ── 预报包裹状态回推 ────────────────────────────────────────────────────────

    /**
     * HBR pushes inbound parcel status updates.
     *
     * Expected body (JSON):
     * {
     *   "appToken":    "7167b176...",   // required — shared secret
     *   "trackingNo":  "SF1234567890",  // inbound tracking number (our inboundTrackingNo)
     *   "status":      "RECEIVED",      // see status map above
     *   "location":    "A01",           // warehouse shelf/location (optional)
     *   "weightG":     1500,            // actual weight in grams (optional)
     *   "remark":      "..."            // free-text note (optional)
     * }
     *
     * Returns: {"success":1} or {"success":0,"message":"..."}.
     * HBR expects HTTP 200 regardless — non-200 triggers retries.
     */
    @PostMapping("/inbound")
    public ResponseEntity<Map<String, Object>> inboundCallback(@RequestBody Map<String, Object> body) {
        if (!validateToken(body)) {
            log.warn("HBR inbound callback: invalid appToken");
            return ResponseEntity.ok(Map.of("success", 0, "message", "invalid token"));
        }

        String trackingNo = getString(body, "trackingNo");
        String statusStr  = getString(body, "status");
        String location   = getString(body, "location");
        Integer weightG   = getInt(body, "weightG");
        String remark     = getString(body, "remark");

        if (trackingNo == null || trackingNo.isBlank()) {
            log.warn("HBR inbound callback: missing trackingNo");
            return ResponseEntity.ok(Map.of("success", 0, "message", "trackingNo required"));
        }

        Optional<ForwardingParcelEntity> opt = parcelRepository.findByInboundTrackingNo(trackingNo.trim());
        if (opt.isEmpty()) {
            log.warn("HBR inbound callback: no parcel found for trackingNo={}", trackingNo);
            // Return success so HBR doesn't retry indefinitely for unknown numbers
            return ResponseEntity.ok(Map.of("success", 1, "message", "parcel not found — ignored"));
        }

        ForwardingParcelEntity parcel = opt.get();
        ParcelStatus newStatus = mapInboundStatus(statusStr);

        if (newStatus == null) {
            log.warn("HBR inbound callback: unrecognised status '{}' for trackingNo={}", statusStr, trackingNo);
            return ResponseEntity.ok(Map.of("success", 0, "message", "unknown status: " + statusStr));
        }

        // Only advance status — never go backwards
        if (newStatus.ordinal() > parcel.getStatus().ordinal()) {
            log.info("HBR inbound callback: parcel {} ({}) {} → {}",
                    parcel.getId(), trackingNo, parcel.getStatus(), newStatus);
            parcel.setStatus(newStatus);

            if (newStatus == ParcelStatus.IN_WAREHOUSE && parcel.getCheckinDate() == null) {
                parcel.setCheckinDate(LocalDateTime.now());
            }
            if (location != null && !location.isBlank()) {
                parcel.setWarehouseLocation(location.trim());
            }
            if (weightG != null && weightG > 0) {
                parcel.setWeight(weightG);
            }
            if (remark != null && !remark.isBlank()) {
                String existing = parcel.getNotes() != null ? parcel.getNotes() : "";
                parcel.setNotes((existing.isBlank() ? "" : existing + "\n") + "[HBR] " + remark);
            }
            parcelRepository.save(parcel);
        } else {
            log.info("HBR inbound callback: parcel {} already at {} — skipping status {}", parcel.getId(), parcel.getStatus(), newStatus);
        }

        return ResponseEntity.ok(Map.of("success", 1));
    }

    // ── 合箱打包状态回推 ────────────────────────────────────────────────────────

    /**
     * HBR pushes consolidated shipment status updates.
     *
     * Expected body (JSON):
     * {
     *   "appToken":          "7167b176...",     // required — shared secret
     *   "packingNo":         "HX20260414-ABC",  // our order number (orderNo / packingNo)
     *   "outboundTrackingNo":"1Z9999W99999999", // HBR outbound tracking (may be new)
     *   "carrier":           "UPS",             // outbound carrier (optional)
     *   "status":            "SHIPPED",         // see status map above
     *   "feeJpy":            3200,              // actual shipping fee in JPY (optional)
     *   "weightG":           2500,              // actual packed weight in grams (optional)
     *   "remark":            "..."              // free-text note (optional)
     * }
     *
     * On SHIPPED: sets outboundTrackingNo + carrier on the order and all linked parcels.
     * On PACKING: sets order status → PACKING, updates weight / fee if provided.
     * On DELIVERED: marks order and all linked parcels as DELIVERED.
     * feeJpy: stored as shippingFeeCny (JPY → CNY converted) and quotedFeeJpy. Only written
     *         when the order has no fee yet, or the new value differs.
     */
    @PostMapping("/shipment")
    public ResponseEntity<Map<String, Object>> shipmentCallback(@RequestBody Map<String, Object> body) {
        if (!validateToken(body)) {
            log.warn("HBR shipment callback: invalid appToken");
            return ResponseEntity.ok(Map.of("success", 0, "message", "invalid token"));
        }

        String packingNo          = getString(body, "packingNo");
        String outboundTrackingNo = getString(body, "outboundTrackingNo");
        String carrier            = getString(body, "carrier");
        String statusStr          = getString(body, "status");
        Integer feeJpy            = getInt(body, "feeJpy");
        Integer weightG           = getInt(body, "weightG");
        String remark             = getString(body, "remark");

        if (packingNo == null || packingNo.isBlank()) {
            log.warn("HBR shipment callback: missing packingNo");
            return ResponseEntity.ok(Map.of("success", 0, "message", "packingNo required"));
        }

        // Look up by orderNo first (HX... prefix used for direct shipping requests),
        // then by packingNo field if present.
        Optional<OrderEntity> opt = orderRepository.findByOrderNo(packingNo.trim());
        if (opt.isEmpty()) {
            opt = orderRepository.findByPackingNo(packingNo.trim());
        }
        if (opt.isEmpty()) {
            log.warn("HBR shipment callback: no order found for packingNo={}", packingNo);
            return ResponseEntity.ok(Map.of("success", 1, "message", "order not found — ignored"));
        }

        OrderEntity order = opt.get();
        OrderStatus newOrderStatus = mapShipmentOrderStatus(statusStr);
        ParcelStatus newParcelStatus = mapShipmentParcelStatus(statusStr);

        if (newOrderStatus == null) {
            log.warn("HBR shipment callback: unrecognised status '{}' for packingNo={}", statusStr, packingNo);
            return ResponseEntity.ok(Map.of("success", 0, "message", "unknown status: " + statusStr));
        }

        log.info("HBR shipment callback: order {} ({}) → status={} outbound={} carrier={} feeJpy={}",
                order.getId(), packingNo, newOrderStatus, outboundTrackingNo, carrier, feeJpy);

        // Update shipping fee if HBR provides it
        if (feeJpy != null && feeJpy > 0) {
            java.math.BigDecimal feeCny = new java.math.BigDecimal(feeJpy)
                    .multiply(JPY_TO_CNY)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            order.setShippingFeeCny(feeCny);
            order.setQuotedFeeJpy(feeJpy);
            log.info("HBR shipment callback: set shippingFee ¥{} JPY (≈¥{} CNY) on order {}",
                    feeJpy, feeCny, order.getOrderNo());
        }

        // Update weight if provided
        if (weightG != null && weightG > 0 && order.getWeightG() == null) {
            order.setWeightG(weightG);
        }

        // Set outbound tracking + carrier when HBR provides them
        if (outboundTrackingNo != null && !outboundTrackingNo.isBlank()) {
            order.setTransitTrackingNo(outboundTrackingNo.trim());
        }
        if (carrier != null && !carrier.isBlank()) {
            order.setTransitCarrier(carrier.trim());
        }
        if (remark != null && !remark.isBlank()) {
            String existing = order.getNotes() != null ? order.getNotes() : "";
            order.setNotes((existing.isBlank() ? "" : existing + "\n") + "[HBR] " + remark);
        }

        // Advance order status
        if (shouldAdvanceOrderStatus(order.getStatus(), newOrderStatus)) {
            order.setStatus(newOrderStatus);
        }
        orderRepository.save(order);

        // Propagate status to all linked parcels
        if (newParcelStatus != null) {
            List<ForwardingParcelEntity> linkedParcels = parcelRepository.findByLinkedOrderId(order.getId());
            for (ForwardingParcelEntity parcel : linkedParcels) {
                if (newParcelStatus.ordinal() > parcel.getStatus().ordinal()) {
                    parcel.setStatus(newParcelStatus);
                    if (outboundTrackingNo != null && !outboundTrackingNo.isBlank()) {
                        parcel.setOutboundTrackingNo(outboundTrackingNo.trim());
                    }
                }
            }
            if (!linkedParcels.isEmpty()) {
                parcelRepository.saveAll(linkedParcels);
                log.info("HBR shipment callback: updated {} linked parcels to {}", linkedParcels.size(), newParcelStatus);
            }
        }

        return ResponseEntity.ok(Map.of("success", 1));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private boolean validateToken(Map<String, Object> body) {
        String token = getString(body, "appToken");
        return configuredAppToken != null && configuredAppToken.equals(token);
    }

    private String getString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof String s ? s : null;
    }

    private Integer getInt(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {} }
        return null;
    }

    /** Map HBR inbound status string → ParcelStatus. Returns null for unknown values. */
    private ParcelStatus mapInboundStatus(String s) {
        if (s == null) return null;
        return switch (s.toUpperCase().trim()) {
            case "RECEIVED", "已收货", "IN_WAREHOUSE", "入库", "ARRIVED" -> ParcelStatus.IN_WAREHOUSE;
            case "PACKING", "打包中", "SORTING"                          -> ParcelStatus.PACKING;
            case "SHIPPED", "已发货", "DISPATCHED", "SENT"               -> ParcelStatus.SHIPPED;
            case "DELIVERED", "已签收", "COMPLETED"                      -> ParcelStatus.DELIVERED;
            default -> null;
        };
    }

    /** Map HBR shipment status → OrderStatus. */
    private OrderStatus mapShipmentOrderStatus(String s) {
        if (s == null) return null;
        return switch (s.toUpperCase().trim()) {
            case "PACKING", "打包中", "打包完成", "PACKED"                -> OrderStatus.PACKING;
            case "SHIPPED", "已发货", "DISPATCHED", "SENT"               -> OrderStatus.SHIPPED;
            case "DELIVERED", "已签收", "COMPLETED"                      -> OrderStatus.DELIVERED;
            default -> null;
        };
    }

    /** Map HBR shipment status → ParcelStatus for linked parcels. */
    private ParcelStatus mapShipmentParcelStatus(String s) {
        if (s == null) return null;
        return switch (s.toUpperCase().trim()) {
            case "PACKING", "打包中", "打包完成", "PACKED"                -> ParcelStatus.PACKING;
            case "SHIPPED", "已发货", "DISPATCHED", "SENT"               -> ParcelStatus.SHIPPED;
            case "DELIVERED", "已签收", "COMPLETED"                      -> ParcelStatus.DELIVERED;
            default -> null;
        };
    }

    /** Only advance status, never go backwards. */
    private boolean shouldAdvanceOrderStatus(OrderStatus current, OrderStatus next) {
        // Define progression index
        List<OrderStatus> progression = List.of(
                OrderStatus.PENDING_PAYMENT, OrderStatus.FEE_QUOTED, OrderStatus.PURCHASING,
                OrderStatus.IN_TRANSIT, OrderStatus.IN_WAREHOUSE, OrderStatus.PACKING,
                OrderStatus.AWAITING_PAYMENT, OrderStatus.SHIPPED, OrderStatus.DELIVERED
        );
        int ci = progression.indexOf(current);
        int ni = progression.indexOf(next);
        return ni > ci;
    }
}
