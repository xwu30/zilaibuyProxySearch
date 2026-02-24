package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailDto(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalCny,
        String notes,
        Long userId,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderDetailDto from(OrderEntity e) {
        List<OrderItemDto> items = e.getItems().stream().map(OrderItemDto::from).toList();
        return new OrderDetailDto(e.getId(), e.getOrderNo(), e.getStatus().name(),
                e.getTotalCny(), e.getNotes(), e.getUser().getId(),
                items, e.getCreatedAt(), e.getUpdatedAt());
    }
}
