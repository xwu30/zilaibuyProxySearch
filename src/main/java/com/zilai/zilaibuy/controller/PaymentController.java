package com.zilai.zilaibuy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.entity.VasRequestEntity;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.repository.VasRequestRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final java.math.BigDecimal JPY_TO_CNY = new java.math.BigDecimal("0.0467");    // 积累：1积分 ≈ 1 JPY
    private static final java.math.BigDecimal POINTS_CNY_RATE = JPY_TO_CNY.divide(new java.math.BigDecimal("10"), 6, java.math.RoundingMode.HALF_UP); // 兑换：10积分 = 1 JPY → 1积分 = 0.00467 CNY

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VasRequestRepository vasRequestRepository;
    private final com.zilai.zilaibuy.service.AppSettingService appSettingService;
    private final com.zilai.zilaibuy.service.EmailService emailService;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    record CreateIntentRequest(Long orderId, Integer pointsToUse) {}
    record CreateIntentResponse(String clientSecret) {}
    record ConfirmPaymentRequest(Long orderId) {}
    record ConfirmPaymentResponse(String status) {}
    record BalancePaymentResponse(String status, int pointsEarned) {}
    record CreateShippingIntentRequest(Long orderId, String shippingRoute, java.math.BigDecimal shippingFeeCny,
                                       Integer routeFeeJpy, Integer handlingFeeJpy,
                                       Integer inspectionFeeJpy, Integer photoFeeJpy,
                                       Integer balanceDeductJpy) {}

    @PostMapping("/create-intent")
    public ResponseEntity<CreateIntentResponse> createIntent(
            @RequestBody CreateIntentRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (!order.getUser().getId().equals(currentUser.id())) {
            return ResponseEntity.status(403).build();
        }
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT) {
            return ResponseEntity.badRequest().build();
        }

        // Validate and apply ZilaiPoints discount
        int pointsToUse = req.pointsToUse() != null ? req.pointsToUse() : 0;
        if (pointsToUse > 0) {
            UserEntity user = userRepository.findById(currentUser.id())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            if (pointsToUse > user.getPoints()) {
                return ResponseEntity.badRequest().build();
            }
        }

        // Convert CNY to JPY (zero-decimal currency for Stripe)
        long totalJpy = order.getTotalCny()
                .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        long discountJpy = pointsToUse / 10; // 10 points = 1 JPY
        long chargeJpy = Math.max(50L, totalJpy + 200L - discountJpy); // +200 JPY proxy service fee

        Stripe.apiKey = stripeSecretKey;

        try {
            long serviceFeeJpy = 200L;
            String desc = order.getOrderNo() + " | 商品¥" + totalJpy + " 服务费¥" + serviceFeeJpy;
            if (pointsToUse > 0 && discountJpy > 0) {
                desc += " 积分-¥" + discountJpy + "(" + pointsToUse + "分)";
            }
            desc += " 合计¥" + chargeJpy;
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(chargeJpy)
                    .setCurrency("jpy")
                    .setDescription(desc)
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("orderNo", order.getOrderNo())
                    .putMetadata("userId", String.valueOf(currentUser.id()))
                    .putMetadata("userPhone", currentUser.phone())
                    .putMetadata("pointsUsed", String.valueOf(pointsToUse))
                    .putMetadata("discountJpy", String.valueOf(discountJpy))
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            order.setStripePaymentIntentId(intent.getId());
            order.setPointsUsed(pointsToUse);
            order.setServiceFeeJpy((int) serviceFeeJpy);
            orderRepository.save(order);

            log.info("PaymentIntent created: {} for order {}", intent.getId(), order.getOrderNo());
            return ResponseEntity.ok(new CreateIntentResponse(intent.getClientSecret()));

        } catch (Exception e) {
            log.error("Failed to create PaymentIntent for order {}", order.getId(), e);
            throw new RuntimeException("支付初始化失败，请重试");
        }
    }

    // ── 余额支付 ─────────────────────────────────────────────────────────────────
    record PayWithBalanceRequest(Long orderId) {}

    /**
     * Pay order fully with wallet balance (stored in JPY).
     * Awards points for the earnable portion (service fee or full shipping fee).
     * Recharge itself never awards points — only spending does.
     */
    @PostMapping("/pay-with-balance")
    public ResponseEntity<BalancePaymentResponse> payWithBalance(
            @RequestBody PayWithBalanceRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();

        boolean isShipping = order.getStatus() == OrderEntity.OrderStatus.AWAITING_PAYMENT;
        boolean isProxy    = order.getStatus() == OrderEntity.OrderStatus.PENDING_PAYMENT;
        if (!isShipping && !isProxy)
            return ResponseEntity.badRequest().build();

        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        java.math.BigDecimal balance = user.getBalanceCny() != null ? user.getBalanceCny() : java.math.BigDecimal.ZERO;

        // Calculate required JPY
        long requiredJpy;
        int pointsToEarn;
        if (isShipping) {
            if (order.getShippingFeeCny() == null) return ResponseEntity.badRequest().build();
            requiredJpy = order.getShippingFeeCny()
                    .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();
            pointsToEarn = (int) requiredJpy;  // full shipping fee → points
        } else {
            long itemJpy = order.getTotalCny()
                    .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();
            long serviceFeeJpy = 200L;
            long pointsDiscount = order.getPointsUsed() / 10L;
            requiredJpy = Math.max(0, itemJpy + serviceFeeJpy - pointsDiscount);
            pointsToEarn = (int) serviceFeeJpy;  // service fee only → points (not item price)
        }

        if (balance.longValue() < requiredJpy) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "余额不足");
        }

        // Deduct balance
        user.setBalanceCny(balance.subtract(new java.math.BigDecimal(requiredJpy)));
        userRepository.save(user);

        // Update order and award points
        if (isShipping) {
            order.setStatus(OrderEntity.OrderStatus.PACKING);
            orderRepository.save(order);
            processShippingPaymentPoints(order);
            log.info("Order {} shipping paid with balance ({} JPY), awarded {} points, status → PACKING", order.getOrderNo(), requiredJpy, pointsToEarn);
        } else {
            order.setServiceFeeJpy(200);
            order.setStatus(OrderEntity.OrderStatus.PURCHASING);
            orderRepository.save(order);
            processProxyPaymentPoints(order);
            log.info("Order {} proxy paid with balance ({} JPY), awarded {} points, status → PURCHASING", order.getOrderNo(), requiredJpy, pointsToEarn);
        }

        return ResponseEntity.ok(new BalancePaymentResponse(order.getStatus().name(), pointsToEarn));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    record PayWithPointsRequest(Long orderId, Integer pointsToUse) {}

    @PostMapping("/pay-with-points")
    public ResponseEntity<ConfirmPaymentResponse> payWithPoints(
            @RequestBody PayWithPointsRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT)
            return ResponseEntity.badRequest().build();

        int pointsToUse = req.pointsToUse() != null ? req.pointsToUse() : 0;
        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (pointsToUse > user.getPoints())
            return ResponseEntity.badRequest().build();

        long totalJpy = order.getTotalCny()
                .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();
        long discountJpy = pointsToUse / 10L;
        long serviceFeeJpy = 200L;
        if (discountJpy < totalJpy + serviceFeeJpy)
            return ResponseEntity.badRequest().build(); // points don't fully cover — use Stripe

        order.setPointsUsed(pointsToUse);
        order.setServiceFeeJpy((int) serviceFeeJpy);
        order.setStatus(OrderEntity.OrderStatus.PURCHASING);
        orderRepository.save(order);

        // Deduct points (no earned points since no cash paid)
        int afterDeduct = Math.max(0, user.getPoints() - pointsToUse);
        user.setPoints(afterDeduct);
        userRepository.save(user);

        log.info("Order {} paid fully with {} points, status → PURCHASING", order.getOrderNo(), pointsToUse);
        return ResponseEntity.ok(new ConfirmPaymentResponse("PURCHASING"));
    }

    @PostMapping("/create-shipping-intent")
    public ResponseEntity<CreateIntentResponse> createShippingIntent(
            @RequestBody CreateShippingIntentRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id())) {
            return ResponseEntity.status(403).build();
        }
        if (order.getStatus() != OrderEntity.OrderStatus.AWAITING_PAYMENT) {
            return ResponseEntity.badRequest().build();
        }
        if (req.shippingFeeCny() == null || req.shippingFeeCny().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }

        order.setShippingRoute(req.shippingRoute());
        order.setShippingFeeCny(req.shippingFeeCny());
        // Store fee breakdown in serviceFeeMemo as "route:X,handling:X,inspection:X,photo:X"
        if (req.routeFeeJpy() != null) {
            String memo = String.format("route:%d,handling:%d,inspection:%d,photo:%d",
                    req.routeFeeJpy() != null ? req.routeFeeJpy() : 0,
                    req.handlingFeeJpy() != null ? req.handlingFeeJpy() : 0,
                    req.inspectionFeeJpy() != null ? req.inspectionFeeJpy() : 0,
                    req.photoFeeJpy() != null ? req.photoFeeJpy() : 0);
            order.setServiceFeeMemo(memo);
        }

        Stripe.apiKey = stripeSecretKey;
        long shippingJpy = req.shippingFeeCny()
                .divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP)
                .longValue();
        long balanceDeductJpy = req.balanceDeductJpy() != null ? Math.min(req.balanceDeductJpy(), shippingJpy) : 0L;
        long chargeJpy = Math.max(50L, shippingJpy - balanceDeductJpy);

        try {
            String description = balanceDeductJpy > 0
                    ? String.format("运费 %s（余额抵扣 %d JPY，刷卡 %d JPY）", order.getOrderNo(), balanceDeductJpy, chargeJpy)
                    : "运费 " + order.getOrderNo();
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(chargeJpy)
                    .setCurrency("jpy")
                    .setDescription(description)
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("orderNo", order.getOrderNo())
                    .putMetadata("userId", String.valueOf(currentUser.id()))
                    .putMetadata("type", "shipping")
                    .putMetadata("totalJpy", String.valueOf(shippingJpy));
            if (balanceDeductJpy > 0) {
                paramsBuilder.putMetadata("balanceDeductJpy", String.valueOf(balanceDeductJpy));
                paramsBuilder.putMetadata("chargeJpy", String.valueOf(chargeJpy));
            }
            PaymentIntentCreateParams params = paramsBuilder.build();

            PaymentIntent intent = PaymentIntent.create(params);
            order.setStripePaymentIntentId(intent.getId());
            orderRepository.save(order);

            log.info("Shipping PaymentIntent created: {} for order {}", intent.getId(), order.getOrderNo());
            return ResponseEntity.ok(new CreateIntentResponse(intent.getClientSecret()));
        } catch (Exception e) {
            log.error("Failed to create shipping PaymentIntent for order {}", order.getId(), e);
            throw new RuntimeException("支付初始化失败，请重试");
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmPaymentResponse> confirmPayment(
            @RequestBody ConfirmPaymentRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (!order.getUser().getId().equals(currentUser.id())) {
            return ResponseEntity.status(403).build();
        }
        if (order.getStripePaymentIntentId() == null) {
            return ResponseEntity.badRequest().build();
        }
        // Already confirmed
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT
                && order.getStatus() != OrderEntity.OrderStatus.AWAITING_PAYMENT) {
            return ResponseEntity.ok(new ConfirmPaymentResponse(order.getStatus().name()));
        }

        boolean isShippingPayment = order.getStatus() == OrderEntity.OrderStatus.AWAITING_PAYMENT;

        Stripe.apiKey = stripeSecretKey;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(order.getStripePaymentIntentId());
            if ("succeeded".equals(intent.getStatus())) {
                // Deduct balance if partial balance was used
                String balanceDeductStr = intent.getMetadata() != null ? intent.getMetadata().get("balanceDeductJpy") : null;
                if (balanceDeductStr != null) {
                    try {
                        long deductJpy = Long.parseLong(balanceDeductStr);
                        UserEntity user = userRepository.findById(currentUser.id())
                                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
                        java.math.BigDecimal currentBalance = user.getBalanceCny() != null ? user.getBalanceCny() : java.math.BigDecimal.ZERO;
                        java.math.BigDecimal deductCny = new java.math.BigDecimal(deductJpy);
                        user.setBalanceCny(currentBalance.subtract(deductCny).max(java.math.BigDecimal.ZERO));
                        userRepository.save(user);
                        log.info("Order {} deducted {} JPY balance on confirm", order.getOrderNo(), deductJpy);
                    } catch (Exception e) {
                        log.warn("Failed to deduct balance for order {}: {}", order.getOrderNo(), e.getMessage());
                    }
                }
                if (isShippingPayment) {
                    order.setStatus(OrderEntity.OrderStatus.PACKING);
                    orderRepository.save(order);
                    long cardCharge = intent.getAmount() != null ? intent.getAmount() : -1L;
                    processShippingPaymentPoints(order, cardCharge);
                    sendWarehouseShipEmail(order);
                    log.info("Order {} shipping paid (card {} JPY), status → PACKING", order.getOrderNo(), cardCharge);
                } else {
                    order.setStatus(OrderEntity.OrderStatus.PURCHASING);
                    orderRepository.save(order);
                    processProxyPaymentPoints(order);
                    log.info("Order {} confirmed as PURCHASING via client-side confirmation", order.getOrderNo());
                }
            }
            return ResponseEntity.ok(new ConfirmPaymentResponse(order.getStatus().name()));
        } catch (Exception e) {
            log.error("Failed to confirm payment for order {}", order.getId(), e);
            throw new RuntimeException("确认支付状态失败，请刷新页面查看订单");
        }
    }

    // ── VAS Payment ─────────────────────────────────────────────────────────────

    private static final java.util.Map<String, Long> VAS_FEE_JPY = java.util.Map.of(
            "item_inspect", 4300L,   // ≈ ¥200 CNY
            "photo",        6400L,   // ≈ ¥300 CNY
            "special_pack", 6400L
    );

    record CreateVasIntentRequest(Long vasRequestId) {}

    @PostMapping("/vas/create-intent")
    public ResponseEntity<CreateIntentResponse> createVasIntent(
            @RequestBody CreateVasIntentRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        VasRequestEntity vas = vasRequestRepository.findById(req.vasRequestId())
                .orElseThrow(() -> new IllegalArgumentException("增值服务申请不存在"));
        if (!vas.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();
        if (vas.getStatus() != VasRequestEntity.VasStatus.DONE)
            return ResponseEntity.badRequest().build();

        long amountJpy = 0;
        for (String svc : vas.getServices().split(",")) {
            amountJpy += VAS_FEE_JPY.getOrDefault(svc.trim(), 0L);
        }
        if (amountJpy < 50) amountJpy = 50;

        Stripe.apiKey = stripeSecretKey;
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountJpy)
                    .setCurrency("jpy")
                    .setDescription("增值服务费 " + vas.getServices() + " (vasId=" + vas.getId() + ")")
                    .putMetadata("vasRequestId", String.valueOf(vas.getId()))
                    .putMetadata("userId", String.valueOf(currentUser.id()))
                    .putMetadata("type", "vas")
                    .build();
            PaymentIntent intent = PaymentIntent.create(params);
            vas.setStripePaymentIntentId(intent.getId());
            vasRequestRepository.save(vas);
            log.info("VAS PaymentIntent created: {} for vasId={}", intent.getId(), vas.getId());
            return ResponseEntity.ok(new CreateIntentResponse(intent.getClientSecret()));
        } catch (Exception e) {
            log.error("Failed to create VAS PaymentIntent for vasId={}", vas.getId(), e);
            throw new RuntimeException("支付初始化失败，请重试");
        }
    }

    record ConfirmVasRequest(Long vasRequestId) {}

    @PostMapping("/vas/confirm")
    public ResponseEntity<java.util.Map<String, String>> confirmVasPayment(
            @RequestBody ConfirmVasRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        VasRequestEntity vas = vasRequestRepository.findById(req.vasRequestId())
                .orElseThrow(() -> new IllegalArgumentException("增值服务申请不存在"));
        if (!vas.getUser().getId().equals(currentUser.id()))
            return ResponseEntity.status(403).build();
        if (vas.getStripePaymentIntentId() == null)
            return ResponseEntity.badRequest().build();

        Stripe.apiKey = stripeSecretKey;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(vas.getStripePaymentIntentId());
            if ("succeeded".equals(intent.getStatus())) {
                if (vas.getStatus() == VasRequestEntity.VasStatus.DONE) {
                    vas.setStatus(VasRequestEntity.VasStatus.PAID);
                    vasRequestRepository.save(vas);
                    log.info("VAS request {} marked as PAID", vas.getId());
                }
                return ResponseEntity.ok(java.util.Map.of("status", vas.getStatus().name()));
            }
            return ResponseEntity.ok(java.util.Map.of("status", vas.getStatus().name()));
        } catch (Exception e) {
            log.error("Failed to confirm VAS payment for vasId={}", vas.getId(), e);
            throw new RuntimeException("确认支付状态失败，请刷新页面");
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        byte[] payload;
        try {
            payload = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Failed to read webhook payload", e);
            return ResponseEntity.badRequest().build();
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("STRIPE_WEBHOOK_SECRET not configured — webhooks will not be processed. Set this env var to enable payment status updates.");
            return ResponseEntity.ok().build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(new String(payload), sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            return ResponseEntity.badRequest().build();
        }

        log.info("Stripe webhook received: {}", event.getType());

        // Parse the PaymentIntent ID directly from raw JSON to avoid SDK API-version deserialization issues
        String piId;
        try {
            JsonNode rawJson = new ObjectMapper().readTree(payload);
            piId = rawJson.path("data").path("object").path("id").asText(null);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload JSON", e);
            return ResponseEntity.badRequest().build();
        }
        if (piId == null || piId.isBlank()) {
            log.warn("Could not extract PaymentIntent ID from webhook event");
            return ResponseEntity.ok().build();
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            log.info("Payment succeeded for PaymentIntent: {}", piId);

            // Handle wallet top-up via webhook (backup path — confirm endpoint is primary)
            try {
                com.stripe.Stripe.apiKey = stripeSecretKey;
                com.stripe.model.PaymentIntent pi = com.stripe.model.PaymentIntent.retrieve(piId);
                if ("wallet_topup".equals(pi.getMetadata().get("type"))) {
                    String userIdStr = pi.getMetadata().get("userId");
                    String amountCnyStr = pi.getMetadata().get("amountCny");
                    if (userIdStr != null && amountCnyStr != null) {
                        Long userId = Long.parseLong(userIdStr);
                        java.math.BigDecimal amountCny = new java.math.BigDecimal(amountCnyStr);
                        userRepository.findById(userId).ifPresent(user -> {
                            // Idempotency: only credit if balance hasn't been updated
                            // (confirm endpoint already credited; webhook is backup)
                            // We can't know if confirm ran, so we check nothing — idempotency
                            // must be handled by the client not calling confirm twice.
                            // Webhook is the authoritative path; confirm is optimistic.
                            // To avoid double-credit: skip if confirm already ran.
                            // Simple approach: track via a processed set (omitted for now).
                            // Production: use a WalletTopupEntity table keyed by paymentIntentId.
                            log.info("Webhook: wallet topup {} already handled by confirm or will be ignored", piId);
                        });
                    }
                    return ResponseEntity.ok().build();
                }
            } catch (Exception e) {
                log.error("Webhook: failed to check wallet_topup type for {}", piId, e);
            }

            // Handle VAS payment
            vasRequestRepository.findByStripePaymentIntentId(piId).ifPresent(vas -> {
                if (vas.getStatus() == VasRequestEntity.VasStatus.DONE) {
                    vas.setStatus(VasRequestEntity.VasStatus.PAID);
                    vasRequestRepository.save(vas);
                    log.info("VAS request {} marked as PAID (webhook)", vas.getId());
                }
            });

            orderRepository.findByStripePaymentIntentId(piId).ifPresent(order -> {
                if (order.getStatus() == OrderEntity.OrderStatus.AWAITING_PAYMENT) {
                    // Shipping payment: confirm hasn't run yet
                    order.setStatus(OrderEntity.OrderStatus.PACKING);
                    orderRepository.save(order);
                    String chargeStr = event.getDataObjectDeserializer().getObject()
                            .map(obj -> ((com.stripe.model.PaymentIntent) obj).getAmount())
                            .map(String::valueOf).orElse("-1");
                    processShippingPaymentPoints(order, Long.parseLong(chargeStr));
                    sendWarehouseShipEmail(order);
                    log.info("Order {} shipping paid (webhook), status → PACKING", order.getOrderNo());
                } else if (order.getStatus() == OrderEntity.OrderStatus.PENDING_PAYMENT) {
                    // Proxy payment: confirm hasn't run yet
                    order.setStatus(OrderEntity.OrderStatus.PURCHASING);
                    orderRepository.save(order);
                    processProxyPaymentPoints(order);
                    log.info("Order {} status updated to PURCHASING (webhook)", order.getOrderNo());
                }
                // PACKING or PURCHASING: confirm already ran — skip to avoid double-processing
            });
        } else if ("payment_intent.payment_failed".equals(event.getType())) {
            log.warn("Payment failed for PaymentIntent: {}", piId);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Called after proxy order payment succeeds.
     * Deducts used points; awards points ONLY for the service fee (not item price).
     */
    private void processProxyPaymentPoints(OrderEntity order) {
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            int used = order.getPointsUsed();
            int afterDeduct = Math.max(0, user.getPoints() - used);

            // Only service fee earns points — item purchase price does NOT
            long serviceFeeJpy = order.getServiceFeeJpy() != null ? order.getServiceFeeJpy().longValue() : 200L;
            int earned = (int) Math.max(0, serviceFeeJpy);

            user.setPoints(afterDeduct + earned);
            userRepository.save(user);
            log.info("Order {} (proxy): deducted {} points, awarded {} points (service fee only). User {} → {} points",
                    order.getOrderNo(), used, earned, user.getId(), user.getPoints());
        });
    }

    /**
     * Called after shipping payment succeeds.
     * Awards points for the full shipping fee in JPY.
     */
    private void processShippingPaymentPoints(OrderEntity order) {
        processShippingPaymentPoints(order, -1L);
    }

    /** @param cardChargeJpy actual amount charged to card (excludes balance). -1 = use full shipping fee. */
    private void processShippingPaymentPoints(OrderEntity order, long cardChargeJpy) {
        if (order.getShippingFeeCny() == null) return;
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            long pointsBase = cardChargeJpy >= 0 ? cardChargeJpy
                    : order.getShippingFeeCny().divide(JPY_TO_CNY, 0, java.math.RoundingMode.HALF_UP).longValue();
            int earned = (int) Math.max(0, pointsBase);
            user.setPoints(user.getPoints() + earned);
            userRepository.save(user);
            log.info("Order {} (shipping): awarded {} points (card charge {} JPY). User {} → {} points",
                    order.getOrderNo(), earned, cardChargeJpy, user.getId(), user.getPoints());
        });
    }

    private void sendWarehouseShipEmail(OrderEntity order) {
        String warehouseEmail = appSettingService.get("warehouse.dispatch.email", null);
        if (warehouseEmail == null || warehouseEmail.isBlank()) return;
        try {
            emailService.sendWarehouseDispatchEmail(warehouseEmail, order);
        } catch (Exception e) {
            log.warn("Failed to send warehouse dispatch email for order {}: {}", order.getOrderNo(), e.getMessage());
        }
    }
}
