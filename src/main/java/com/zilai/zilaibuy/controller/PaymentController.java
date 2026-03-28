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
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
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

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    record CreateIntentRequest(Long orderId, Integer pointsToUse) {}
    record CreateIntentResponse(String clientSecret) {}
    record ConfirmPaymentRequest(Long orderId) {}
    record ConfirmPaymentResponse(String status) {}
    record CreateShippingIntentRequest(Long orderId, String shippingRoute, java.math.BigDecimal shippingFeeCny) {}

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

        BigDecimal discount = BigDecimal.valueOf(pointsToUse).multiply(POINTS_CNY_RATE);
        BigDecimal chargeAmount = order.getTotalCny().subtract(discount);
        if (chargeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            chargeAmount = new BigDecimal("0.01");
        }

        Stripe.apiKey = stripeSecretKey;

        long amountFen = chargeAmount
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        try {
            String desc = "采购费 " + order.getOrderNo();
            if (pointsToUse > 0) {
                desc += " | 积分抵扣 " + pointsToUse + "分(-¥" + discount.setScale(2, java.math.RoundingMode.HALF_UP) + ")";
            }
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountFen)
                    .setCurrency("cny")
                    .setDescription(desc)
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("orderNo", order.getOrderNo())
                    .putMetadata("userId", String.valueOf(currentUser.id()))
                    .putMetadata("userPhone", currentUser.phone())
                    .putMetadata("pointsUsed", String.valueOf(pointsToUse))
                    .putMetadata("discountCny", discount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            order.setStripePaymentIntentId(intent.getId());
            order.setPointsUsed(pointsToUse);
            orderRepository.save(order);

            log.info("PaymentIntent created: {} for order {}", intent.getId(), order.getOrderNo());
            return ResponseEntity.ok(new CreateIntentResponse(intent.getClientSecret()));

        } catch (Exception e) {
            log.error("Failed to create PaymentIntent for order {}", order.getId(), e);
            throw new RuntimeException("支付初始化失败，请重试");
        }
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

        Stripe.apiKey = stripeSecretKey;
        long amountFen = req.shippingFeeCny().multiply(BigDecimal.valueOf(100)).longValue();

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(Math.max(amountFen, 1L))
                    .setCurrency("cny")
                    .setDescription("运费 " + order.getOrderNo())
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("orderNo", order.getOrderNo())
                    .putMetadata("userId", String.valueOf(currentUser.id()))
                    .putMetadata("type", "shipping")
                    .build();

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
                if (isShippingPayment) {
                    order.setStatus(OrderEntity.OrderStatus.PACKING);
                    log.info("Order {} shipping paid, status → PACKING", order.getOrderNo());
                } else {
                    order.setStatus(OrderEntity.OrderStatus.PURCHASING);
                    deductPoints(order);
                    log.info("Order {} confirmed as PURCHASING via client-side confirmation", order.getOrderNo());
                }
                orderRepository.save(order);
            }
            return ResponseEntity.ok(new ConfirmPaymentResponse(order.getStatus().name()));
        } catch (Exception e) {
            log.error("Failed to confirm payment for order {}", order.getId(), e);
            throw new RuntimeException("确认支付状态失败，请刷新页面查看订单");
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
            orderRepository.findByStripePaymentIntentId(piId).ifPresent(order -> {
                if (order.getStatus() == OrderEntity.OrderStatus.AWAITING_PAYMENT) {
                    order.setStatus(OrderEntity.OrderStatus.PACKING);
                    orderRepository.save(order);
                    log.info("Order {} shipping paid (webhook), status → PACKING", order.getOrderNo());
                } else if (order.getStatus() != OrderEntity.OrderStatus.PURCHASING) {
                    order.setStatus(OrderEntity.OrderStatus.PURCHASING);
                    orderRepository.save(order);
                    deductPoints(order);
                    log.info("Order {} status updated to PURCHASING", order.getOrderNo());
                }
            });
        } else if ("payment_intent.payment_failed".equals(event.getType())) {
            log.warn("Payment failed for PaymentIntent: {}", piId);
        }

        return ResponseEntity.ok().build();
    }

    private void deductPoints(OrderEntity order) {
        userRepository.findById(order.getUser().getId()).ifPresent(user -> {
            // Deduct used points
            int used = order.getPointsUsed();
            int afterDeduct = Math.max(0, user.getPoints() - used);

            // Award earned points: floor(net paid CNY / JPY_TO_CNY) → 1 point per JPY
            java.math.BigDecimal netPaid = order.getTotalCny()
                    .subtract(java.math.BigDecimal.valueOf(used).multiply(POINTS_CNY_RATE));
            if (netPaid.compareTo(java.math.BigDecimal.ZERO) < 0) netPaid = java.math.BigDecimal.ZERO;
            int earned = netPaid.divide(JPY_TO_CNY, 0, java.math.RoundingMode.FLOOR).intValue();

            user.setPoints(afterDeduct + earned);
            userRepository.save(user);
            log.info("Order {}: deducted {} points, awarded {} points. User {} now has {} points",
                    order.getOrderNo(), used, earned, user.getId(), user.getPoints());
        });
    }
}
