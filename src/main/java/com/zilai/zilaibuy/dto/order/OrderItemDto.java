package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.entity.OrderItemEntity;

import java.math.BigDecimal;

public record OrderItemDto(
        Long id,
        String productTitle,
        String originalUrl,
        int priceJpy,
        BigDecimal priceCny,
        int quantity,
        String remarks,
        BigDecimal domesticShipping,
        BigDecimal exchangeRate,
        String platform,
        String imageUrl
) {
    public static OrderItemDto from(OrderItemEntity e) {
        return new OrderItemDto(e.getId(), e.getProductTitle(), e.getOriginalUrl(),
                e.getPriceJpy(), e.getPriceCny(), e.getQuantity(), e.getRemarks(),
                e.getDomesticShipping(), e.getExchangeRate(), e.getPlatform(), e.getImageUrl());
    }
}
