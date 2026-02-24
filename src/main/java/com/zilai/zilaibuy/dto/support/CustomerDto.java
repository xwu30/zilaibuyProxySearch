package com.zilai.zilaibuy.dto.support;

import java.time.LocalDateTime;

public record CustomerDto(
        Long id,
        String phone,
        String role,
        long orderCount,
        LocalDateTime lastOrderAt
) {}
