package com.zilai.zilaibuy.dto.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDto(
        Long id,
        String orderNo,
        String packingNo,
        Long userId,
        String userPhone,
        String username,
        String status,
        BigDecimal totalCny,
        int itemCount,
        int linkedParcelCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer serviceFeeJpy,
        String serviceFeeMemo,
        String shippingRoute,
        String transitTrackingNo,
        String transitCarrier,
        String firstItemImageUrl,
        Integer firstItemPriceJpy,
        String firstItemTitle,
        String firstItemOriginalUrl
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AdminOrderDto from(OrderEntity o) {
        var firstItem = (o.getItems() != null && !o.getItems().isEmpty()) ? o.getItems().get(0) : null;
        // Use imageUrl if available, otherwise fall back to first reference image (S3 URL)
        String imageUrl = firstItem != null ? firstItem.getImageUrl() : null;
        if (imageUrl == null && firstItem != null && firstItem.getReferenceImages() != null) {
            try {
                List<String> refs = MAPPER.readValue(firstItem.getReferenceImages(), new TypeReference<List<String>>() {});
                if (refs != null && !refs.isEmpty()) imageUrl = refs.get(0);
            } catch (Exception ignored) {}
        }
        return new AdminOrderDto(
                o.getId(),
                o.getOrderNo(),
                o.getPackingNo(),
                o.getUser().getId(),
                o.getUser().getPhone(),
                o.getUser().getUsername(),
                o.getStatus().name(),
                o.getTotalCny(),
                o.getItems() != null ? o.getItems().size() : 0,
                o.getLinkedParcels() != null ? o.getLinkedParcels().size() : 0,
                o.getCreatedAt(),
                o.getUpdatedAt(),
                o.getServiceFeeJpy(),
                o.getServiceFeeMemo(),
                o.getShippingRoute(),
                o.getTransitTrackingNo(),
                o.getTransitCarrier(),
                imageUrl,
                firstItem != null ? firstItem.getPriceJpy() : null,
                firstItem != null ? firstItem.getProductTitle() : null,
                firstItem != null ? firstItem.getOriginalUrl() : null
        );
    }
}
