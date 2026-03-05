package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalCny,
        String notes,
        String transitTrackingNo,
        String transitCarrier,
        Long userId,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderDto from(OrderEntity e) {
        List<OrderItemDto> items = e.getItems() != null
                ? e.getItems().stream().map(OrderItemDto::from).toList()
                : List.of();
        return new OrderDto(e.getId(), e.getOrderNo(), e.getStatus().name(),
                e.getTotalCny(), e.getNotes(), e.getTransitTrackingNo(), e.getTransitCarrier(),
                e.getUser().getId(), items, e.getCreatedAt(), e.getUpdatedAt());
    }
}
