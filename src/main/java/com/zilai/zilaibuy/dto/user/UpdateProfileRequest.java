package com.zilai.zilaibuy.dto.user;

public record UpdateProfileRequest(
        String displayName,
        String shippingFullName,
        String shippingPhone,
        String shippingStreet,
        String shippingCity,
        String shippingProvince,
        String shippingPostalCode
) {}
