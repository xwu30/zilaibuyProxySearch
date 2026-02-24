package com.zilai.zilaibuy.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank String productTitle,
        String originalUrl,
        @NotNull int priceJpy,
        @NotNull BigDecimal priceCny,
        @Min(1) int quantity,
        String remarks,
        BigDecimal domesticShipping,
        @NotNull BigDecimal exchangeRate,
        String platform,
        String imageUrl
) {}
