package com.zilai.zilaibuy.dto.auth;

public record RefreshResponse(String accessToken, String refreshToken, long expiresIn) {}
