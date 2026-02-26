package com.zilai.zilaibuy.dto.user;

public record ProfileDto(
        Long id,
        String displayName,
        String email,
        String phone,
        String shippingFullName,
        String shippingPhone,
        String shippingStreet,
        String shippingCity,
        String shippingProvince,
        String shippingPostalCode
) {}
