package com.zilai.zilaibuy.dto.admin;

import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        String transitCarrier
) {
    public static AdminOrderDto from(OrderEntity o) {
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
                o.getTransitCarrier()
        );
    }
}
