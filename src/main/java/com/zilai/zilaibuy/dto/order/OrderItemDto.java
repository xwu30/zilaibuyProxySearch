package com.zilai.zilaibuy.dto.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.entity.OrderItemEntity;

import java.math.BigDecimal;
import java.util.List;

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
        String imageUrl,
        String itemStatus,
        String itemTrackingNo,
        String itemCarrier,
        List<String> referenceImages,
        String hbrError
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static OrderItemDto from(OrderItemEntity e) {
        List<String> refs = null;
        if (e.getReferenceImages() != null) {
            try {
                refs = MAPPER.readValue(e.getReferenceImages(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }
        return new OrderItemDto(e.getId(), e.getProductTitle(), e.getOriginalUrl(),
                e.getPriceJpy(), e.getPriceCny(), e.getQuantity(), e.getRemarks(),
                e.getDomesticShipping(), e.getExchangeRate(), e.getPlatform(), e.getImageUrl(),
                e.getItemStatus(), e.getItemTrackingNo(), e.getItemCarrier(), refs, null);
    }

    public OrderItemDto withHbrError(String error) {
        return new OrderItemDto(id, productTitle, originalUrl, priceJpy, priceCny, quantity, remarks,
                domesticShipping, exchangeRate, platform, imageUrl, itemStatus, itemTrackingNo, itemCarrier,
                referenceImages, error);
    }
}
