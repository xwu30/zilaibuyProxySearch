package com.zilai.zilaibuy.dto.admin;

import com.zilai.zilaibuy.entity.UserEntity;

import java.time.LocalDateTime;

public record AdminUserDto(
        Long id,
        String phone,
        String username,
        String email,
        String displayName,
        String role,
        String cloudId,
        boolean isActive,
        boolean isLocked,
        LocalDateTime lockUntil,
        LocalDateTime createdAt,
        String shippingFullName,
        String shippingPhone,
        String shippingStreet,
        String shippingCity,
        String shippingProvince,
        String shippingPostalCode,
        int points
) {
    public static AdminUserDto from(UserEntity e) {
        return new AdminUserDto(
                e.getId(), e.getPhone(), e.getUsername(), e.getEmail(),
                e.getDisplayName(), e.getRole().name(), e.getCloudId(),
                e.isActive(), e.isLocked(), e.getLockUntil(), e.getCreatedAt(),
                e.getShippingFullName(), e.getShippingPhone(), e.getShippingStreet(),
                e.getShippingCity(), e.getShippingProvince(), e.getShippingPostalCode(),
                e.getPoints()
        );
    }
}
