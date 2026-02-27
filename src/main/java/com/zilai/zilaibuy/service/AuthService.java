package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.auth.*;
import com.zilai.zilaibuy.entity.EmailConfirmationEntity;
import com.zilai.zilaibuy.entity.OtpEntity;
import com.zilai.zilaibuy.entity.PendingEmailRegistrationEntity;
import com.zilai.zilaibuy.entity.RefreshTokenEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.EmailConfirmationRepository;
import com.zilai.zilaibuy.repository.PendingEmailRegistrationRepository;
import com.zilai.zilaibuy.repository.RefreshTokenRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailConfirmationRepository emailConfirmationRepository;
    private final EmailService emailService;
    private final PendingEmailRegistrationRepository pendingEmailRegistrationRepository;

    @Value("${jwt.refresh-expiry-days:7}")
    private int refreshExpiryDays;

    @Transactional
    public AuthResponse register(String phone, String code, String password) {
        otpService.verifyOtp(phone, code, OtpEntity.Purpose.REGISTER);

        if (userRepository.existsByPhone(phone)) {
            throw new AppException(HttpStatus.CONFLICT, "手机号已注册");
        }

        UserEntity user = new UserEntity();
        user.setPhone(phone);
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public String registerWithEmail(String email, String password) {
        UserEntity user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            if (user.isEmailVerified()) {
                throw new AppException(HttpStatus.CONFLICT, "邮箱已注册");
            }
            // Email exists but not confirmed — update password and resend confirmation
            if (StringUtils.hasText(password)) {
                user.setPasswordHash(passwordEncoder.encode(password));
                userRepository.save(user);
            }
            emailConfirmationRepository.deleteByUser(user);
        } else {
            user = new UserEntity();
            user.setEmail(email);
            if (StringUtils.hasText(password)) {
                user.setPasswordHash(passwordEncoder.encode(password));
            }
            user.setPhone("email:" + email);
            userRepository.save(user);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        EmailConfirmationEntity confirmation = new EmailConfirmationEntity();
        confirmation.setUser(user);
        confirmation.setToken(token);
        confirmation.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailConfirmationRepository.save(confirmation);

        emailService.sendConfirmationEmail(email, token);
        return token;
    }

    // ── New 3-step email registration ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> sendEmailRegistrationOtp(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            if (u.isEmailVerified()) {
                throw new AppException(HttpStatus.CONFLICT, "该邮箱已注册");
            }
        });

        String devCode = otpService.sendEmailOtp(email, OtpEntity.Purpose.EMAIL_REGISTER);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "验证码已发送到您的邮箱");
        resp.put("expiresIn", 300);
        if (devCode != null) {
            resp.put("devCode", devCode);
        }
        return resp;
    }

    @Transactional
    public Map<String, Object> verifyEmailRegistrationOtp(String email, String code) {
        otpService.verifyOtp(email, code, OtpEntity.Purpose.EMAIL_REGISTER);

        pendingEmailRegistrationRepository.deleteByEmail(email);

        PendingEmailRegistrationEntity pending = new PendingEmailRegistrationEntity();
        pending.setToken(UUID.randomUUID().toString().replace("-", ""));
        pending.setEmail(email);
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        pendingEmailRegistrationRepository.save(pending);

        return Map.of("registrationToken", pending.getToken());
    }

    @Transactional
    public AuthResponse completeEmailRegistration(String registrationToken, String username, String password) {
        PendingEmailRegistrationEntity pending = pendingEmailRegistrationRepository.findByToken(registrationToken)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "注册会话无效或已过期"));

        if (pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            pendingEmailRegistrationRepository.delete(pending);
            throw new AppException(HttpStatus.BAD_REQUEST, "注册会话已过期，请重新验证邮箱");
        }

        if (userRepository.existsByUsername(username)) {
            throw new AppException(HttpStatus.CONFLICT, "用户名已被使用");
        }

        String email = pending.getEmail();
        userRepository.findByEmail(email).ifPresent(u -> {
            if (u.isEmailVerified()) {
                throw new AppException(HttpStatus.CONFLICT, "该邮箱已注册");
            }
        });

        UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setEmail(email);
            u.setPhone("email:" + email);
            return u;
        });

        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        userRepository.save(user);

        pendingEmailRegistrationRepository.delete(pending);

        return buildAuthResponse(user);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse confirmEmail(String token) {
        EmailConfirmationEntity confirmation = emailConfirmationRepository.findByToken(token)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "确认链接无效"));

        if (confirmation.isUsed()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "链接已使用");
        }
        if (confirmation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "链接已过期，请重新注册");
        }

        UserEntity user = confirmation.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        confirmation.setUsed(true);
        emailConfirmationRepository.save(confirmation);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithOtp(String phone, String code) {
        UserEntity user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));

        checkLocked(user);
        otpService.verifyOtp(phone, code, OtpEntity.Purpose.LOGIN);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithPassword(String account, String password) {
        UserEntity user = account.contains("@")
                ? userRepository.findByEmail(account)
                        .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"))
                : userRepository.findByPhone(account)
                        .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));

        checkLocked(user);

        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(password, user.getPasswordHash())) {
            handleLoginFailure(user);
            throw new AppException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }

        // Reset fail count on success
        user.setLoginFailCount(0);
        user.setLastFailAt(null);
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Refresh token 无效"));

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(entity);
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token 已过期");
        }

        UserEntity user = entity.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user);
        return new RefreshResponse(newAccessToken, jwtUtil.getExpirySeconds());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private AuthResponse buildAuthResponse(UserEntity user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String rawRefresh = UUID.randomUUID().toString();

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(user);
        rt.setTokenHash(sha256(rawRefresh));
        rt.setExpiresAt(LocalDateTime.now().plusDays(refreshExpiryDays));
        refreshTokenRepository.save(rt);

        String displayName = user.getShippingFullName();
        if (displayName == null || displayName.isBlank()) {
            displayName = user.getDisplayName();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = "紫来淘客" + String.format("%06d", user.getId());
        }
        UserDto userDto = new UserDto(user.getId(), user.getPhone(), user.getEmail(), displayName, user.getRole().name());
        return new AuthResponse(accessToken, rawRefresh, jwtUtil.getExpirySeconds(), userDto);
    }

    private void checkLocked(UserEntity user) {
        if (user.isLocked()) {
            if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
                throw new AppException(HttpStatus.valueOf(423), "账户已锁定，请稍后重试");
            }
            // Lock expired — unlock
            user.setLocked(false);
            user.setLockUntil(null);
            userRepository.save(user);
        }
    }

    private void handleLoginFailure(UserEntity user) {
        user.setLoginFailCount(user.getLoginFailCount() + 1);
        user.setLastFailAt(LocalDateTime.now());
        if (user.getLoginFailCount() >= 10 &&
                user.getLastFailAt().isAfter(LocalDateTime.now().minusHours(1))) {
            user.setLocked(true);
            user.setLockUntil(LocalDateTime.now().plusHours(1));
        }
        userRepository.save(user);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }
}
