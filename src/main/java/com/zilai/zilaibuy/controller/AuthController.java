package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.auth.*;
import com.zilai.zilaibuy.entity.OtpEntity;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.AuthService;
import com.zilai.zilaibuy.service.OtpService;
import com.zilai.zilaibuy.service.WechatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;
    private final WechatService wechatService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> sendOtp(@Valid @RequestBody OtpSendRequest req) {
        if (req.purpose() == OtpEntity.Purpose.LOGIN) {
            authService.sendLoginOtp(req.phone());
        } else {
            otpService.sendOtp(req.phone(), req.purpose());
        }
        return ResponseEntity.ok(Map.of("message", "验证码已发送", "expiresIn", 300));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req.phone(), req.code(), req.password()));
    }

    @PostMapping("/register/email")
    public ResponseEntity<Map<String, Object>> registerWithEmail(@Valid @RequestBody EmailRegisterRequest req) {
        authService.registerWithEmail(req.email(), req.password());
        return ResponseEntity.ok(Map.of("message", "确认邮件已发送，请检查您的邮箱"));
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@RequestParam String token) {
        AuthResponse auth = authService.confirmEmail(token);
        String redirect = frontendUrl + "/?confirmed=1&accessToken=" + auth.accessToken()
                + "&refreshToken=" + auth.refreshToken();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect)
                .build();
    }

    // ── 三步邮箱注册 ──────────────────────────────────────────────────────────

    @PostMapping("/email/otp/send")
    public ResponseEntity<Map<String, Object>> sendEmailOtp(@Valid @RequestBody EmailOtpSendRequest req) {
        return ResponseEntity.ok(authService.sendEmailRegistrationOtp(req.email()));
    }

    @PostMapping("/email/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyEmailOtp(@Valid @RequestBody EmailOtpVerifyRequest req) {
        return ResponseEntity.ok(authService.verifyEmailRegistrationOtp(req.email(), req.code()));
    }

    @PostMapping("/email/register/complete")
    public ResponseEntity<AuthResponse> completeEmailRegister(@Valid @RequestBody EmailRegisterCompleteRequest req) {
        return ResponseEntity.ok(authService.completeEmailRegistration(
                req.registrationToken(), req.username(), req.password()));
    }

    @GetMapping("/username/check")
    public ResponseEntity<Map<String, Object>> checkUsername(@RequestParam String username) {
        if (username == null || username.length() < 3 || username.length() > 30) {
            return ResponseEntity.ok(Map.of("available", false));
        }
        boolean available = authService.isUsernameAvailable(username);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/login/otp")
    public ResponseEntity<AuthResponse> loginWithOtp(@Valid @RequestBody OtpLoginRequest req) {
        return ResponseEntity.ok(authService.loginWithOtp(req.phone(), req.code()));
    }

    @PostMapping("/login/password")
    public ResponseEntity<AuthResponse> loginWithPassword(@Valid @RequestBody PasswordLoginRequest req) {
        return ResponseEntity.ok(authService.loginWithPassword(req.account(), req.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        authService.logout(currentUser.id());
        return ResponseEntity.ok(Map.of("message", "已登出"));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody PasswordResetRequestDto req) {
        authService.requestPasswordReset(req.account());
        return ResponseEntity.ok(Map.of("message", "如果该账号存在，重置链接已发送到绑定邮箱"));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetDto req) {
        authService.resetPassword(req.token(), req.newPassword(), req.confirmPassword());
        return ResponseEntity.ok(Map.of("message", "密码已重置，请重新登录"));
    }

    @PostMapping("/wechat-login")
    public ResponseEntity<AuthResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest req) {
        return ResponseEntity.ok(wechatService.login(req.code()));
    }
}
