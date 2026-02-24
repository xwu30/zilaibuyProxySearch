package com.zilai.zilaibuy.security;

public record AuthenticatedUser(Long id, String phone, String role) {
}
