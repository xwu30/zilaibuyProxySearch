package com.zilai.zilaibuy.dto.warehouse;

import com.zilai.zilaibuy.entity.ProductEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDto(
        Long id,
        String title,
        String description,
        BigDecimal priceCny,
        int stockQuantity,
        boolean isPublished,
        List<String> imageUrls,
        LocalDateTime createdAt
) {
    public static ProductDto from(ProductEntity e) {
        List<String> urls = e.getImages().stream().map(img -> img.getImageUrl()).toList();
        return new ProductDto(e.getId(), e.getTitle(), e.getDescription(), e.getPriceCny(),
                e.getStockQuantity(), e.isPublished(), urls, e.getCreatedAt());
    }
}
