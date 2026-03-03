package com.zilai.zilaibuy.controller;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.repository.OrderRepository;
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

    private final OrderRepository orderRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    record CreateIntentRequest(Long orderId) {}
    record CreateIntentResponse(String clientSecret) {}

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

        Stripe.apiKey = stripeSecretKey;

        long amountFen = order.getTotalCny()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountFen)
                    .setCurrency("cny")
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .putMetadata("orderNo", order.getOrderNo())
                    .putMetadata("userId", String.valueOf(currentUser.id()))
                    .putMetadata("userPhone", currentUser.phone())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            order.setStripePaymentIntentId(intent.getId());
            orderRepository.save(order);

            log.info("PaymentIntent created: {} for order {}", intent.getId(), order.getOrderNo());
            return ResponseEntity.ok(new CreateIntentResponse(intent.getClientSecret()));

        } catch (Exception e) {
            log.error("Failed to create PaymentIntent for order {}", order.getId(), e);
            throw new RuntimeException("支付初始化失败，请重试");
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

        if ("payment_intent.succeeded".equals(event.getType())) {
            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            if (intent != null) {
                orderRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(order -> {
                    order.setStatus(OrderEntity.OrderStatus.PURCHASING);
                    orderRepository.save(order);
                    log.info("Order {} status updated to PURCHASING", order.getOrderNo());
                });
            }
        } else if ("payment_intent.payment_failed".equals(event.getType())) {
            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            if (intent != null) {
                log.warn("Payment failed for PaymentIntent: {}", intent.getId());
            }
        }

        return ResponseEntity.ok().build();
    }
}
