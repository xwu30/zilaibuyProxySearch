package com.zilai.zilaibuy.dto.admin;

import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderDto(
        Long id,
        String orderNo,
        Long userId,
        String userPhone,
        String username,
        String status,
        BigDecimal totalCny,
        int itemCount,
        LocalDateTime createdAt
) {
    public static AdminOrderDto from(OrderEntity o) {
        return new AdminOrderDto(
                o.getId(),
                o.getOrderNo(),
                o.getUser().getId(),
                o.getUser().getPhone(),
                o.getUser().getUsername(),
                o.getStatus().name(),
                o.getTotalCny(),
                o.getItems() != null ? o.getItems().size() : 0,
                o.getCreatedAt()
        );
    }
}
