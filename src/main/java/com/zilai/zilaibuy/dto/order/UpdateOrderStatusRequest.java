package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.entity.OrderEntity;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderEntity.OrderStatus status, String notes) {}
