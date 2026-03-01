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
        boolean isActive,
        boolean isLocked,
        LocalDateTime lockUntil,
        LocalDateTime createdAt
) {
    public static AdminUserDto from(UserEntity e) {
        return new AdminUserDto(e.getId(), e.getPhone(), e.getUsername(), e.getEmail(),
                e.getDisplayName(), e.getRole().name(),
                e.isActive(), e.isLocked(), e.getLockUntil(), e.getCreatedAt());
    }
}
