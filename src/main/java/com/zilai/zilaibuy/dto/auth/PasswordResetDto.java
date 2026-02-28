package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetDto(
        @NotBlank String token,
        @NotBlank
        @Size(min = 8, max = 100, message = "密码至少需要8位")
        @Pattern(regexp = "^(?=.*\\d)(?=.*[^A-Za-z0-9]).*$", message = "密码必须包含数字和特殊符号")
        String newPassword,
        @NotBlank(message = "请确认新密码") String confirmPassword
) {}
