package com.zilai.zilaibuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.dto.auth.AuthResponse;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.RefreshTokenRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.JwtUtil;
import com.zilai.zilaibuy.entity.RefreshTokenEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${wechat.appid:}")
    private String appId;

    @Value("${wechat.secret:}")
    private String appSecret;

    @Value("${jwt.refresh-expiry-days:7}")
    private int refreshExpiryDays;

    private static final SecureRandom SECURE_RNG = new SecureRandom();

    @Transactional
    public AuthResponse login(String code) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录暂未配置");
        }

        String openId = fetchOpenId(code);

        UserEntity user = userRepository.findByWechatOpenId(openId).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setWechatOpenId(openId);
            u.setPhone("wx:" + openId);
            u.setCloudId(generateCloudId());
            return userRepository.save(u);
        });

        if (!user.isActive()) {
            throw new AppException(HttpStatus.FORBIDDEN, "账户已被禁用，请联系客服");
        }

        return buildAuthResponse(user);
    }

    private String fetchOpenId(String code) {
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session"
                    + "?appid=" + appId
                    + "&secret=" + appSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";

            String body = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                log.error("微信 jscode2session 失败: {}", body);
                throw new AppException(HttpStatus.UNAUTHORIZED, "微信登录失败，code 无效或已过期");
            }

            return node.get("openid").asText();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信接口异常", e);
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "微信服务暂时不可用");
        }
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
        if (displayName == null || displayName.isBlank()) displayName = user.getDisplayName();
        if (displayName == null || displayName.isBlank())
            displayName = "紫来淘客" + String.format("%06d", user.getId());

        com.zilai.zilaibuy.dto.auth.UserDto userDto = new com.zilai.zilaibuy.dto.auth.UserDto(
                user.getId(), user.getPhone(), user.getEmail(),
                displayName, user.getRole().name(), user.getCloudId(), user.getPoints());

        return new AuthResponse(accessToken, rawRefresh, jwtUtil.getExpirySeconds(), userDto);
    }

    private String generateCloudId() {
        String id;
        do {
            id = "ZL" + String.format("%06d", SECURE_RNG.nextInt(1_000_000));
        } while (userRepository.existsByCloudId(id));
        return id;
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
