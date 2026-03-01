package com.zilai.zilaibuy.dto.admin;

import java.math.BigDecimal;

public record AdminStatsDto(
        long totalUsers,
        long totalOrders,
        BigDecimal totalRevenueCny,
        long pendingOrders,
        long purchasingOrders,
        long inWarehouseOrders,
        long shippedOrders
) {}
