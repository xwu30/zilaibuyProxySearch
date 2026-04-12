package com.zilai.zilaibuy.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(
        @NotBlank(message = "code 不能为空") String code
) {}
