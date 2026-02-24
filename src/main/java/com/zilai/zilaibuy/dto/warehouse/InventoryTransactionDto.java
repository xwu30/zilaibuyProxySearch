package com.zilai.zilaibuy.dto.warehouse;

import com.zilai.zilaibuy.entity.InventoryTransactionEntity;

import java.time.LocalDateTime;

public record InventoryTransactionDto(
        Long id,
        Long inventoryId,
        String type,
        int quantityDelta,
        int quantityAfter,
        Long operatorId,
        String notes,
        LocalDateTime createdAt
) {
    public static InventoryTransactionDto from(InventoryTransactionEntity e) {
        return new InventoryTransactionDto(e.getId(), e.getInventory().getId(),
                e.getType().name(), e.getQuantityDelta(), e.getQuantityAfter(),
                e.getOperator().getId(), e.getNotes(), e.getCreatedAt());
    }
}
