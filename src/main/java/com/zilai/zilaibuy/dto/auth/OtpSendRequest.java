package com.zilai.zilaibuy.dto.auth;

import com.zilai.zilaibuy.entity.OtpEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OtpSendRequest(
        @NotBlank String phone,
        @NotNull OtpEntity.Purpose purpose
) {}
