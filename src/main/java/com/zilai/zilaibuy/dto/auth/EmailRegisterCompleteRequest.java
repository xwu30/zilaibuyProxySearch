package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailRegisterCompleteRequest(
        @NotBlank String registrationToken,
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线") String username,
        @NotBlank @Size(min = 6, max = 100) String password
) {}
