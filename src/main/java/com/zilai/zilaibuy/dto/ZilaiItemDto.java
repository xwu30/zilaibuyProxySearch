package com.zilai.zilaibuy.dto;

public record ZilaiItemDto(
        String title,
        String caption,
        String url,
        String shopName,
        Integer priceJPY,
        String imageUrl
) {}
