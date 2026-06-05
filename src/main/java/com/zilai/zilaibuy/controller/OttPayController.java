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
import com.zilai.zilaibuy.service.OttPayService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * OTT Pay (WeChat Pay) payment endpoints.
 *
 * Flow:
 *   1. Frontend calls /create-order   → server creates OTT Pay H5 order → returns payUrl
 *   2. User opens payUrl in WeChat and pays
 *   3. OTT Pay POSTs to /callback (async notification)
 *   4. Frontend also calls /capture after user returns, to confirm status
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/ottPay")
@RequiredArgsConstructor
public class OttPayController {

    // 1 JPY ≈ 0.0467 CNY; 1 CNY = 100 fen
    private static final BigDecimal JPY_TO_CNY     = new BigDecimal("0.0467");
    private static final BigDecimal CNY_TO_FEN     = new BigDecimal("100");
    private static final Map<String, Long> VAS_FEE_JPY = Map.of(
            "item_inspect", 200L,
            "photo",        300L,
            "special_pack", 300L
    );

    private final OrderRepository       orderRepository;
    private final UserRepository        userRepository;
    private final VasRequestRepository  vasRequestRepository;
    private final OttPayService         ottPayService;
    private final AppSettingService     appSettingService;
    private final EmailService          emailService;
    private final HbrService            hbrService;

    @Value("${app.base-url:https://api.zilaibuy.com}")
    private String appBaseUrl;

    private String callbackUrl() {
        return appBaseUrl + "/api/payments/ottPay/callback";
    }

    /** Convert JPY amount to CNY fen (1 CNY = 100 fen). */
    private long jpyToCnyFen(long jpy) {
        return BigDecimal.valueOf(jpy)
                .multiply(JPY_TO_CNY)
                .multiply(CNY_TO_FEN)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    // ── Proxy order ───────────────────────────────────────────────────────────

    record CreateOrderRequest(Long orderId, Integer pointsToUse) {}

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createProxyOrder(
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
                .divide(JPY_TO_CNY, 0, RoundingMode.HALF_UP).longValue();
        long discountJpy = pointsToUse / 10L;
        long chargeJpy   = Math.max(1L, totalJpy + 200L - discountJpy);

        String ottOrderRef = "ZBP" + order.getId() + "T" + System.currentTimeMillis();
        String qrCode = ottPayService.createQrPayOrder(chargeJpy, ottOrderRef, callbackUrl());

        order.setOttPayOrderRef(ottOrderRef);
        order.setPointsUsed(pointsToUse);
        order.setServiceFeeJpy(200);
        orderRepository.save(order);

        log.info("[OttPay] Proxy order created ref={} for order {}", ottOrderRef, order.getOrderNo());
        return ResponseEntity.ok(Map.of("qrCode", qrCode, "ottOrderRef", ottOrderRef));
    }

    record CaptureRequest(Long orderId, String ottOrderRef) {}

    @PostMapping("/capture")
    @Transactional
    public ResponseEntity<Map<String, Object>> captureProxyOrder(
            @RequestBody CaptureRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();

        if (order.getStatus() == OrderEntity.OrderStatus.PURCHASING)
            return ResponseEntity.ok(Map.of("status", "PURCHASING", "alreadyProcessed", true));
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT)
            return ResponseEntity.badRequest().build();

        Map<String, Object> result = ottPayService.queryOrder(req.ottOrderRef());
        if (!ottPayService.isPaymentCompleted(result)) {
            log.info("[OttPay] Proxy capture not completed for {}: {}", order.getOrderNo(), result.get("order_status"));
            return ResponseEntity.ok(Map.of("status", result.getOrDefault("order_status", "PENDING")));
        }

        order.setStatus(OrderEntity.OrderStatus.PURCHASING);
        orderRepository.save(order);
        processProxyPaymentPoints(order);
        log.info("[OttPay] Proxy order {} paid → PURCHASING", order.getOrderNo());
        return ResponseEntity.ok(Map.of("status", "PURCHASING"));
    }

    // ── Shipping payment ──────────────────────────────────────────────────────

    record CreateShippingRequest(Long orderId, String shippingRoute, BigDecimal shippingFeeCny,
                                  Integer routeFeeJpy, Integer handlingFeeJpy,
                                  Integer inspectionFeeJpy, Integer photoFeeJpy) {}

    @PostMapping("/create-shipping")
    public ResponseEntity<Map<String, Object>> createShippingOrder(
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
                    req.routeFeeJpy()     != null ? req.routeFeeJpy()     : 0,
                    req.handlingFeeJpy()  != null ? req.handlingFeeJpy()  : 0,
                    req.inspectionFeeJpy() != null ? req.inspectionFeeJpy() : 0,
                    req.photoFeeJpy()     != null ? req.photoFeeJpy()     : 0);
            order.setServiceFeeMemo(memo);
        }

        long shippingJpy = Math.max(1L, req.shippingFeeCny()
                .divide(JPY_TO_CNY, 0, RoundingMode.HALF_UP).longValue());

        String ottOrderRef = "ZBS" + order.getId() + "T" + System.currentTimeMillis();
        String qrCode = ottPayService.createQrPayOrder(shippingJpy, ottOrderRef, callbackUrl());

        order.setOttPayOrderRef(ottOrderRef);
        orderRepository.save(order);

        log.info("[OttPay] Shipping order created ref={} for order {}", ottOrderRef, order.getOrderNo());
        return ResponseEntity.ok(Map.of("qrCode", qrCode, "ottOrderRef", ottOrderRef));
    }

    record CaptureShippingRequest(Long orderId, String ottOrderRef) {}

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

        Map<String, Object> result = ottPayService.queryOrder(req.ottOrderRef());
        if (!ottPayService.isPaymentCompleted(result))
            return ResponseEntity.ok(Map.of("status", result.getOrDefault("order_status", "PENDING")));

        order.setStatus(OrderEntity.OrderStatus.PACKING);
        orderRepository.save(order);
        processShippingPaymentPoints(order);
        sendWarehouseShipEmail(order);
        pushHbrPayInfo(order, req.ottOrderRef(), "WeChatPay");
        log.info("[OttPay] Shipping order {} paid → PACKING", order.getOrderNo());
        return ResponseEntity.ok(Map.of("status", "PACKING"));
    }

    // ── VAS payment ───────────────────────────────────────────────────────────

    record CreateVasRequest(Long vasRequestId) {}

    @PostMapping("/create-vas")
    public ResponseEntity<Map<String, Object>> createVasOrder(
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
        String ottOrderRef = "ZBV" + vas.getId() + "T" + System.currentTimeMillis();
        String qrCode = ottPayService.createQrPayOrder(Math.max(1L, amountJpy), ottOrderRef, callbackUrl());

        vas.setOttPayOrderRef(ottOrderRef);
        vasRequestRepository.save(vas);

        return ResponseEntity.ok(Map.of("qrCode", qrCode, "ottOrderRef", ottOrderRef));
    }

    record CaptureVasRequest(Long vasRequestId, String ottOrderRef) {}

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

        Map<String, Object> result = ottPayService.queryOrder(req.ottOrderRef());
        if (!ottPayService.isPaymentCompleted(result))
            return ResponseEntity.ok(Map.of("status", result.getOrDefault("order_status", "PENDING")));

        vas.setStatus(VasRequestEntity.VasStatus.PAID);
        vasRequestRepository.save(vas);
        log.info("[OttPay] VAS {} paid → PAID", vas.getId());
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

        String ottOrderRef = "ZBTU" + currentUser.id() + "M" + (System.currentTimeMillis() % 100000);
        String qrCode = ottPayService.createQrPayOrder(req.amountJpy(), ottOrderRef, callbackUrl());

        return ResponseEntity.ok(Map.of(
                "qrCode", qrCode,
                "ottOrderRef", ottOrderRef,
                "amountJpy", req.amountJpy()));
    }

    record TopupCaptureRequest(String ottOrderRef, Long amountJpy) {}

    @PostMapping("/topup/capture")
    @Transactional
    public ResponseEntity<Map<String, Object>> captureTopup(
            @RequestBody TopupCaptureRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Map<String, Object> result = ottPayService.queryOrder(req.ottOrderRef());
        if (!ottPayService.isPaymentCompleted(result))
            return ResponseEntity.ok(Map.of("status", result.getOrDefault("order_status", "PENDING")));

        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        BigDecimal newBalance = (user.getBalanceJpy() != null ? user.getBalanceJpy() : BigDecimal.ZERO)
                .add(BigDecimal.valueOf(req.amountJpy()));
        user.setBalanceJpy(newBalance);
        userRepository.save(user);

        log.info("[OttPay] Topup captured user={}: +{} JPY, balance={}", currentUser.id(), req.amountJpy(), newBalance);
        return ResponseEntity.ok(Map.of("status", "succeeded", "amountJpy", req.amountJpy(), "newBalanceJpy", newBalance));
    }

    // ── Async callback from OTT Pay ───────────────────────────────────────────

    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Void> handleCallback(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> data = ottPayService.decryptCallback(payload);
            String ottOrderRef   = (String) data.get("order_id");
            String orderStatus   = (String) data.get("order_status");
            log.info("[OttPay] Callback: ref={}, status={}", ottOrderRef, orderStatus);

            if (ottOrderRef == null || !ottPayService.isPaymentCompleted(data))
                return ResponseEntity.ok().build();

            if (ottOrderRef.startsWith("ZBP")) {
                long orderId = Long.parseLong(ottOrderRef.substring(3));
                orderRepository.findById(orderId).ifPresent(order -> {
                    if (order.getStatus() == OrderEntity.OrderStatus.PENDING_PAYMENT) {
                        order.setStatus(OrderEntity.OrderStatus.PURCHASING);
                        orderRepository.save(order);
                        processProxyPaymentPoints(order);
                        log.info("[OttPay] Callback: proxy order {} → PURCHASING", order.getOrderNo());
                    }
                });
            } else if (ottOrderRef.startsWith("ZBS")) {
                long orderId = Long.parseLong(ottOrderRef.substring(3));
                orderRepository.findById(orderId).ifPresent(order -> {
                    if (order.getStatus() == OrderEntity.OrderStatus.AWAITING_PAYMENT) {
                        order.setStatus(OrderEntity.OrderStatus.PACKING);
                        orderRepository.save(order);
                        processShippingPaymentPoints(order);
                        sendWarehouseShipEmail(order);
                        pushHbrPayInfo(order, ottOrderRef, "WeChatPay");
                        log.info("[OttPay] Callback: shipping order {} → PACKING", order.getOrderNo());
                    }
                });
            } else if (ottOrderRef.startsWith("ZBV")) {
                long vasId = Long.parseLong(ottOrderRef.substring(3));
                vasRequestRepository.findById(vasId).ifPresent(vas -> {
                    if (vas.getStatus() == VasRequestEntity.VasStatus.DONE) {
                        vas.setStatus(VasRequestEntity.VasStatus.PAID);
                        vasRequestRepository.save(vas);
                        log.info("[OttPay] Callback: VAS {} → PAID", vasId);
                    }
                });
            }
            // Topup handled via frontend /topup/capture; callback just logs.

        } catch (Exception e) {
            log.error("[OttPay] Callback processing error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void processProxyPaymentPoints(OrderEntity order) {
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            int used = order.getPointsUsed();
            long serviceFeeJpy = order.getServiceFeeJpy() != null ? order.getServiceFeeJpy().longValue() : 200L;
            int earned = (int) Math.max(0, serviceFeeJpy);
            user.setPoints(Math.max(0, user.getPoints() - used) + earned);
            userRepository.save(user);
            log.info("Order {} (ottPay proxy): deducted {} pts, awarded {} pts", order.getOrderNo(), used, earned);
        });
    }

    private void processShippingPaymentPoints(OrderEntity order) {
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            long totalJpy = totalFeesJpy(order);
            if (totalJpy <= 0) return;
            user.setPoints(user.getPoints() + (int) totalJpy);
            userRepository.save(user);
            log.info("Order {} (ottPay shipping): awarded {} pts", order.getOrderNo(), totalJpy);
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
            return order.getShippingFeeCny().divide(JPY_TO_CNY, 0, RoundingMode.HALF_UP).longValue();
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
