package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.auth.*;
import com.zilai.zilaibuy.entity.EmailConfirmationEntity;
import com.zilai.zilaibuy.entity.OtpEntity;
import com.zilai.zilaibuy.entity.RefreshTokenEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.EmailConfirmationRepository;
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
        if (userRepository.findByEmail(email).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, "邮箱已注册");
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        if (StringUtils.hasText(password)) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        // Use email as phone placeholder until phone is set
        user.setPhone("email:" + email);
        userRepository.save(user);

        // Create confirmation token
        String token = UUID.randomUUID().toString().replace("-", "");
        EmailConfirmationEntity confirmation = new EmailConfirmationEntity();
        confirmation.setUser(user);
        confirmation.setToken(token);
        confirmation.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailConfirmationRepository.save(confirmation);

        emailService.sendConfirmationEmail(email, token);
        return token; // returned for dev mode
    }

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

        UserDto userDto = new UserDto(user.getId(), user.getPhone(), user.getRole().name());
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
