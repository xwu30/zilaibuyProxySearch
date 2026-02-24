package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailRegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
