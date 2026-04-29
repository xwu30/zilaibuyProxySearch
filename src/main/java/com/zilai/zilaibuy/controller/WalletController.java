package com.zilai.zilaibuy.controller;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private static final BigDecimal JPY_TO_CNY = new BigDecimal("0.0467");
    private static final long MIN_STRIPE_JPY = 50L;

    private final UserRepository userRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    record TopupIntentRequest(Long amountJpy) {}
    record TopupIntentResponse(String clientSecret, long amountJpy) {}
    record TopupConfirmRequest(String paymentIntentId) {}

    /** Step 1: create Stripe PaymentIntent for wallet top-up (JPY). No points awarded on topup. */
    @PostMapping("/topup/create-intent")
    public ResponseEntity<TopupIntentResponse> createTopupIntent(
            @RequestBody TopupIntentRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        if (req.amountJpy() == null || req.amountJpy() < 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "充值金额最低 500 日元");
        }
        if (req.amountJpy() > 5_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次充值不超过 500 万日元");
        }

        long amountJpy = Math.max(MIN_STRIPE_JPY, req.amountJpy());

        Stripe.apiKey = stripeSecretKey;
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountJpy)
                    .setCurrency("jpy")
                    .setDescription("余额充值 ¥" + amountJpy + " JPY")
                    .putMetadata("type", "wallet_topup")
                    .putMetadata("userId", String.valueOf(principal.id()))
                    .putMetadata("amountJpy", String.valueOf(amountJpy))
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Wallet topup intent {} created for user {}, ¥{} JPY",
                    intent.getId(), principal.id(), amountJpy);
            return ResponseEntity.ok(new TopupIntentResponse(intent.getClientSecret(), amountJpy));
        } catch (Exception e) {
            log.error("Failed to create topup PaymentIntent for user {}", principal.id(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "支付初始化失败，请重试");
        }
    }

    /** Step 2: client-side confirm — verify PaymentIntent succeeded and credit balance. */
    @PostMapping("/topup/confirm")
    public ResponseEntity<Map<String, Object>> confirmTopup(
            @RequestBody TopupConfirmRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        Stripe.apiKey = stripeSecretKey;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(req.paymentIntentId());

            // Verify this intent belongs to this user
            String metaUserId = intent.getMetadata().get("userId");
            if (!String.valueOf(principal.id()).equals(metaUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作");
            }
            if (!"wallet_topup".equals(intent.getMetadata().get("type"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付类型不匹配");
            }
            if (!"succeeded".equals(intent.getStatus())) {
                return ResponseEntity.ok(Map.of("status", intent.getStatus()));
            }

            String amountJpyStr = intent.getMetadata().get("amountJpy");
            if (amountJpyStr == null) amountJpyStr = String.valueOf(intent.getAmount());
            BigDecimal amountJpyBd = new BigDecimal(amountJpyStr);

            UserEntity user = userRepository.findById(principal.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            // balance_cny column repurposed to store JPY balance directly
            BigDecimal newBalance = (user.getBalanceJpy() != null ? user.getBalanceJpy() : BigDecimal.ZERO)
                    .add(amountJpyBd);
            user.setBalanceJpy(newBalance);
            userRepository.save(user);

            log.info("Wallet topup confirmed for user {}: +¥{} JPY, new balance ¥{} JPY (intent {})",
                    principal.id(), amountJpyBd, newBalance, req.paymentIntentId());

            return ResponseEntity.ok(Map.of(
                    "status", "succeeded",
                    "amountJpy", amountJpyBd,
                    "newBalanceJpy", newBalance
            ));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to confirm topup for user {}", principal.id(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "确认充值失败，请联系客服");
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BigDecimal balance = user.getBalanceJpy() != null ? user.getBalanceJpy() : BigDecimal.ZERO;
        return ResponseEntity.ok(Map.of("balanceJpy", balance));
    }
}
