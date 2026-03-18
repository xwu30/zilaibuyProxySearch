package com.zilai.zilaibuy.dto.admin;

public record AdminUpdateUserRequest(
        String username,
        String email,
        String phone,
        String displayName,
        String cloudId,
        String shippingFullName,
        String shippingPhone,
        String shippingStreet,
        String shippingCity,
        String shippingProvince,
        String shippingPostalCode
) {}
