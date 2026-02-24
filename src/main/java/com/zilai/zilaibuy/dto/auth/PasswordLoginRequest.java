package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordLoginRequest(@NotBlank String account, @NotBlank String password) {}
