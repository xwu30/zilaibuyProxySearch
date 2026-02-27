package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailOtpVerifyRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {}
