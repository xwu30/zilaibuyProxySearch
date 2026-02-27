package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailOtpSendRequest(
        @NotBlank @Email String email
) {}
