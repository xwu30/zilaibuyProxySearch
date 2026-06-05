package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.entity.VasRequestEntity;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.repository.VasRequestRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.AppSettingService;
import com.zilai.zilaibuy.service.EmailService;
import com.zilai.zilaibuy.service.HbrService;
import com.zilai.zilaibuy.service.PaypalService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/paypal")
@RequiredArgsConstructor
public class PaypalPaymentController {

    private static final BigDecimal JPY_TO_CNY = new BigDecimal("0.0467");
    private static final Map<String, Long> VAS_FEE_JPY = Map.of(
            "item_inspect", 200L,
            "photo",        300L,
            "special_pack", 300L
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VasRequestRepository vasRequestRepository;
    private final PaypalService paypalService;
    private final AppSettingService appSettingService;
    private final EmailService emailService;
    private final HbrService hbrService;

    // ── Proxy order ───────────────────────────────────────────────────────────

    record CreateOrderRequest(Long orderId, Integer pointsToUse) {}

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, String>> createProxyOrder(
            @RequestBody CreateOrderRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT)
            return ResponseEntity.badRequest().build();

        int pointsToUse = req.pointsToUse() != null ? req.pointsToUse() : 0;
        if (pointsToUse > 0) {
            UserEntity user = userRepository.findById(currentUser.id())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            if (pointsToUse > user.getPoints()) return ResponseEntity.badRequest().build();
        }

        long totalJpy = order.getTotalCny()
                .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();
        long discountJpy = pointsToUse / 10L;
        long chargeJpy = Math.max(1L, totalJpy + 200L - discountJpy);

        String paypalOrderId = paypalService.createOrder(chargeJpy,
                "代购订单 " + order.getOrderNo(), String.valueOf(order.getId()));

        order.setPaypalOrderId(paypalOrderId);
        order.setPointsUsed(pointsToUse);
        order.setServiceFeeJpy(200);
        orderRepository.save(order);

        log.info("PayPal proxy order created {} for order {}", paypalOrderId, order.getOrderNo());
        return ResponseEntity.ok(Map.of("paypalOrderId", paypalOrderId));
    }

    record CaptureRequest(Long orderId, String paypalOrderId) {}

    @PostMapping("/capture")
    @Transactional
    public ResponseEntity<Map<String, Object>> captureProxyOrder(
            @RequestBody CaptureRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();

        // Idempotency: already processed
        if (order.getStatus() == OrderEntity.OrderStatus.PURCHASING) {
            return ResponseEntity.ok(Map.of("status", "PURCHASING", "alreadyProcessed", true));
        }
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT)
            return ResponseEntity.badRequest().build();

        Map<String, Object> result = paypalService.captureOrder(req.paypalOrderId());
        if (!paypalService.isCompleted(result)) {
            log.warn("PayPal capture not completed for order {}: {}", order.getOrderNo(), result.get("status"));
            return ResponseEntity.ok(Map.of("status", result.get("status")));
        }

        order.setStatus(OrderEntity.OrderStatus.PURCHASING);
        orderRepository.save(order);
        processProxyPaymentPoints(order);
        log.info("PayPal proxy order {} captured, status → PURCHASING", order.getOrderNo());
        return ResponseEntity.ok(Map.of("status", "PURCHASING"));
    }

    // ── Shipping payment ──────────────────────────────────────────────────────

    record CreateShippingRequest(Long orderId, String shippingRoute, BigDecimal shippingFeeCny,
                                  Integer routeFeeJpy, Integer handlingFeeJpy,
                                  Integer inspectionFeeJpy, Integer photoFeeJpy) {}

    @PostMapping("/create-shipping")
    public ResponseEntity<Map<String, String>> createShippingOrder(
            @RequestBody CreateShippingRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();
        if (order.getStatus() != OrderEntity.OrderStatus.AWAITING_PAYMENT)
            return ResponseEntity.badRequest().build();
        if (req.shippingFeeCny() == null || req.shippingFeeCny().compareTo(BigDecimal.ZERO) <= 0)
            return ResponseEntity.badRequest().build();

        order.setShippingRoute(req.shippingRoute());
        order.setShippingFeeCny(req.shippingFeeCny());
        if (req.routeFeeJpy() != null) {
            String memo = String.format("route:%d,handling:%d,inspection:%d,photo:%d",
                    req.routeFeeJpy() != null ? req.routeFeeJpy() : 0,
                    req.handlingFeeJpy() != null ? req.handlingFeeJpy() : 0,
                    req.inspectionFeeJpy() != null ? req.inspectionFeeJpy() : 0,
                    req.photoFeeJpy() != null ? req.photoFeeJpy() : 0);
            order.setServiceFeeMemo(memo);
        }

        long shippingJpy = req.shippingFeeCny()
                .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();

        String paypalOrderId = paypalService.createOrder(Math.max(1L, shippingJpy),
                "集运运费 " + order.getOrderNo(), "SHIP-" + order.getId());

        order.setPaypalOrderId(paypalOrderId);
        orderRepository.save(order);

        log.info("PayPal shipping order created {} for order {}", paypalOrderId, order.getOrderNo());
        return ResponseEntity.ok(Map.of("paypalOrderId", paypalOrderId));
    }

    record CaptureShippingRequest(Long orderId, String paypalOrderId) {}

    @PostMapping("/capture-shipping")
    @Transactional
    public ResponseEntity<Map<String, Object>> captureShippingOrder(
            @RequestBody CaptureShippingRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();

        if (order.getStatus() == OrderEntity.OrderStatus.PACKING)
            return ResponseEntity.ok(Map.of("status", "PACKING", "alreadyProcessed", true));
        if (order.getStatus() != OrderEntity.OrderStatus.AWAITING_PAYMENT)
            return ResponseEntity.badRequest().build();

        Map<String, Object> result = paypalService.captureOrder(req.paypalOrderId());
        if (!paypalService.isCompleted(result)) {
            return ResponseEntity.ok(Map.of("status", result.get("status")));
        }

        order.setStatus(OrderEntity.OrderStatus.PACKING);
        orderRepository.save(order);
        processShippingPaymentPoints(order);
        sendWarehouseShipEmail(order);
        pushHbrPayInfo(order, req.paypalOrderId(), "PayPal");
        log.info("PayPal shipping order {} captured, status → PACKING", order.getOrderNo());
        return ResponseEntity.ok(Map.of("status", "PACKING"));
    }

    // ── VAS payment ───────────────────────────────────────────────────────────

    record CreateVasRequest(Long vasRequestId) {}

    @PostMapping("/create-vas")
    public ResponseEntity<Map<String, String>> createVasOrder(
            @RequestBody CreateVasRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        VasRequestEntity vas = vasRequestRepository.findById(req.vasRequestId())
                .orElseThrow(() -> new IllegalArgumentException("增值服务申请不存在"));
        if (!vas.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();
        if (vas.getStatus() != VasRequestEntity.VasStatus.DONE)
            return ResponseEntity.badRequest().build();

        long amountJpy;
        if ("custom".equals(vas.getServices())) {
            amountJpy = vas.getAdminQuoteJpy() != null ? vas.getAdminQuoteJpy().longValue() : 0L;
        } else {
            amountJpy = 0;
            for (String svc : vas.getServices().split(","))
                amountJpy += VAS_FEE_JPY.getOrDefault(svc.trim(), 0L);
        }
        amountJpy = Math.max(1L, amountJpy);

        String paypalOrderId = paypalService.createOrder(amountJpy,
                "增值服务 " + vas.getServices(), "VAS-" + vas.getId());

        vas.setPaypalOrderId(paypalOrderId);
        vasRequestRepository.save(vas);

        return ResponseEntity.ok(Map.of("paypalOrderId", paypalOrderId));
    }

    record CaptureVasRequest(Long vasRequestId, String paypalOrderId) {}

    @PostMapping("/capture-vas")
    @Transactional
    public ResponseEntity<Map<String, Object>> captureVasOrder(
            @RequestBody CaptureVasRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        VasRequestEntity vas = vasRequestRepository.findById(req.vasRequestId())
                .orElseThrow(() -> new IllegalArgumentException("增值服务申请不存在"));
        if (!vas.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();

        if (vas.getStatus() == VasRequestEntity.VasStatus.PAID)
            return ResponseEntity.ok(Map.of("status", "PAID", "alreadyProcessed", true));
        if (vas.getStatus() != VasRequestEntity.VasStatus.DONE)
            return ResponseEntity.badRequest().build();

        Map<String, Object> result = paypalService.captureOrder(req.paypalOrderId());
        if (!paypalService.isCompleted(result))
            return ResponseEntity.ok(Map.of("status", result.get("status")));

        vas.setStatus(VasRequestEntity.VasStatus.PAID);
        vasRequestRepository.save(vas);
        log.info("PayPal VAS {} captured, marked as PAID", vas.getId());
        return ResponseEntity.ok(Map.of("status", "PAID"));
    }

    // ── Wallet topup ──────────────────────────────────────────────────────────

    record TopupOrderRequest(Long amountJpy) {}

    @PostMapping("/topup/create")
    public ResponseEntity<Map<String, Object>> createTopupOrder(
            @RequestBody TopupOrderRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        if (req.amountJpy() == null || req.amountJpy() < 500)
            return ResponseEntity.badRequest().build();

        String paypalOrderId = paypalService.createOrder(req.amountJpy(),
                "余额充值 ¥" + req.amountJpy() + " JPY", "TOPUP-" + currentUser.id());

        return ResponseEntity.ok(Map.of("paypalOrderId", paypalOrderId, "amountJpy", req.amountJpy()));
    }

    record TopupCaptureRequest(String paypalOrderId, Long amountJpy) {}

    @PostMapping("/topup/capture")
    @Transactional
    public ResponseEntity<Map<String, Object>> captureTopup(
            @RequestBody TopupCaptureRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Map<String, Object> result = paypalService.captureOrder(req.paypalOrderId());
        if (!paypalService.isCompleted(result))
            return ResponseEntity.ok(Map.of("status", result.get("status")));

        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        BigDecimal newBalance = (user.getBalanceJpy() != null ? user.getBalanceJpy() : BigDecimal.ZERO)
                .add(BigDecimal.valueOf(req.amountJpy()));
        user.setBalanceJpy(newBalance);
        userRepository.save(user);

        log.info("PayPal topup captured for user {}: +{} JPY, new balance {}", currentUser.id(), req.amountJpy(), newBalance);
        return ResponseEntity.ok(Map.of("status", "succeeded", "amountJpy", req.amountJpy(), "newBalanceJpy", newBalance));
    }

    // ── Webhook (backup path) ─────────────────────────────────────────────────

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        String eventType = (String) payload.get("event_type");
        log.info("PayPal webhook received: {}", eventType);

        if (!"PAYMENT.CAPTURE.COMPLETED".equals(eventType)) return ResponseEntity.ok().build();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
            if (resource == null) return ResponseEntity.ok().build();

            // custom_id carries our internal reference
            @SuppressWarnings("unchecked")
            Map<String, Object> supplementary = (Map<String, Object>) resource.get("supplementary_data");
            String customId = null;
            if (supplementary != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> related = (Map<String, Object>) supplementary.get("related_ids");
                if (related != null) customId = (String) related.get("order_id");
            }
            if (customId == null) customId = (String) resource.get("custom_id");

            log.info("PayPal webhook CAPTURE.COMPLETED customId={}", customId);
            // Primary path is the capture endpoint — webhook is backup idempotency guard
        } catch (Exception e) {
            log.error("PayPal webhook processing error", e);
        }
        return ResponseEntity.ok().build();
    }

    // ── Shared helpers (mirrors PaymentController) ────────────────────────────

    private void processProxyPaymentPoints(OrderEntity order) {
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            int used = order.getPointsUsed();
            long serviceFeeJpy = order.getServiceFeeJpy() != null ? order.getServiceFeeJpy().longValue() : 200L;
            int earned = (int) Math.max(0, serviceFeeJpy);
            user.setPoints(Math.max(0, user.getPoints() - used) + earned);
            userRepository.save(user);
            log.info("Order {} (paypal proxy): deducted {} pts, awarded {} pts", order.getOrderNo(), used, earned);
        });
    }

    private void processShippingPaymentPoints(OrderEntity order) {
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            long totalJpy = totalFeesJpy(order);
            if (totalJpy <= 0) return;
            user.setPoints(user.getPoints() + (int) totalJpy);
            userRepository.save(user);
            log.info("Order {} (paypal shipping): awarded {} pts", order.getOrderNo(), totalJpy);
        });
    }

    private long totalFeesJpy(OrderEntity order) {
        if (order.getServiceFeeMemo() != null && order.getServiceFeeMemo().startsWith("route:")) {
            long total = 0;
            for (String part : order.getServiceFeeMemo().split(",")) {
                String[] kv = part.split(":");
                if (kv.length == 2) { try { total += Long.parseLong(kv[1].trim()); } catch (NumberFormatException ignored) {} }
            }
            if (total > 0) return total;
        }
        if (order.getShippingFeeCny() != null)
            return order.getShippingFeeCny().divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();
        return 0;
    }

    private void sendWarehouseShipEmail(OrderEntity order) {
        String email = appSettingService.get("warehouse.dispatch.email", null);
        if (email == null || email.isBlank()) return;
        try { emailService.sendWarehouseDispatchEmail(email, order); }
        catch (Exception e) { log.warn("Failed to send warehouse email for {}: {}", order.getOrderNo(), e.getMessage()); }
    }

    private void pushHbrPayInfo(OrderEntity order, String payCode, String payType) {
        if (order.getPackingNo() == null || order.getPackingNo().isBlank()) return;
        try {
            long totalJpy = totalFeesJpy(order);
            UserEntity user = order.getUser();
            String payInfo = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername() : (user.getPhone() != null ? user.getPhone() : "");
            hbrService.pushOrderPayInfo(order.getPackingNo(), totalJpy, payCode, payInfo, payType, order.getOrderNo());
        } catch (Exception e) { log.warn("pushHbrPayInfo failed for order {}: {}", order.getOrderNo(), e.getMessage()); }
    }
}
