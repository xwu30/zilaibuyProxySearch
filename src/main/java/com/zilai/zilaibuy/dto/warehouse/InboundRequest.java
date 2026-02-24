package com.zilai.zilaibuy.dto.warehouse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InboundRequest(
        Long inventoryId,
        String itemName,
        String sku,
        @NotNull @Min(1) int quantityDelta,
        String notes
) {}
