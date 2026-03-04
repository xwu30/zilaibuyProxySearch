package com.zilai.zilaibuy.dto.order;

import java.math.BigDecimal;

public record UpdateOrderItemRequest(
        int quantity,
        BigDecimal priceCny
) {}
