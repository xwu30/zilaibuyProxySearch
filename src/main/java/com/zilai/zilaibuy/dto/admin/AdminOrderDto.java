package com.zilai.zilaibuy.dto.admin;

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
    public static AdminOrderDto from(OrderEntity o) {
        var firstItem = (o.getItems() != null && !o.getItems().isEmpty()) ? o.getItems().get(0) : null;
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
                firstItem != null ? firstItem.getImageUrl() : null,
                firstItem != null ? firstItem.getPriceJpy() : null,
                firstItem != null ? firstItem.getProductTitle() : null,
                firstItem != null ? firstItem.getOriginalUrl() : null
        );
    }
}
