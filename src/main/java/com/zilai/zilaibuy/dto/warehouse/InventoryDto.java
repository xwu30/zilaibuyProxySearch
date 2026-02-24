package com.zilai.zilaibuy.dto.warehouse;

import com.zilai.zilaibuy.entity.InventoryEntity;

import java.time.LocalDateTime;

public record InventoryDto(
        Long id,
        String itemName,
        String sku,
        int quantity,
        Long productId,
        LocalDateTime updatedAt
) {
    public static InventoryDto from(InventoryEntity e) {
        Long productId = e.getProduct() != null ? e.getProduct().getId() : null;
        return new InventoryDto(e.getId(), e.getItemName(), e.getSku(), e.getQuantity(),
                productId, e.getUpdatedAt());
    }
}
