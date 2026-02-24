package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDto(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalCny,
        String notes,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderDto from(OrderEntity e) {
        return new OrderDto(e.getId(), e.getOrderNo(), e.getStatus().name(),
                e.getTotalCny(), e.getNotes(), e.getUser().getId(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
