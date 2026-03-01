package com.zilai.zilaibuy.dto.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank String title,
        String description,
        @NotNull BigDecimal priceCny,
        int stockQuantity,
        boolean isPublished,
        List<String> imageUrls
) {}
