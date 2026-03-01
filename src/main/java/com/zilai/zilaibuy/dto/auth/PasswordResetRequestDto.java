package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestDto(@NotBlank String account) {}
