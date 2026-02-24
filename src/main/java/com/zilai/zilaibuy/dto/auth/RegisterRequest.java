package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String phone,
        @NotBlank String code,
        String password
) {}
