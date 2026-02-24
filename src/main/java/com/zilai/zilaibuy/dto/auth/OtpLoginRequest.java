package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OtpLoginRequest(@NotBlank String phone, @NotBlank String code) {}
