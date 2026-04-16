package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.user.ProfileDto;
import com.zilai.zilaibuy.dto.user.SendPhoneOtpRequest;
import com.zilai.zilaibuy.dto.user.SetupCredentialsRequest;
import com.zilai.zilaibuy.dto.user.UpdateProfileRequest;
import com.zilai.zilaibuy.dto.user.VerifyPhoneRequest;
import com.zilai.zilaibuy.entity.OtpEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping("/profile")
    public ProfileDto getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toDto(user);
    }

    @PutMapping("/profile")
    public ProfileDto updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UpdateProfileRequest req
    ) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (req.displayName() != null)       user.setDisplayName(req.displayName());
        if (req.shippingFullName() != null)  user.setShippingFullName(req.shippingFullName());
        if (req.shippingPhone() != null)     user.setShippingPhone(req.shippingPhone());
        if (req.shippingStreet() != null)    user.setShippingStreet(req.shippingStreet());
        if (req.shippingCity() != null)      user.setShippingCity(req.shippingCity());
        if (req.shippingProvince() != null)  user.setShippingProvince(req.shippingProvince());
        if (req.shippingPostalCode() != null) user.setShippingPostalCode(req.shippingPostalCode());
        if (req.shippingCountry() != null)   user.setShippingCountry(req.shippingCountry());
        if (req.addressesJson() != null)     user.setAddressesJson(req.addressesJson());

        userRepository.save(user);
        return toDto(user);
    }

    @PostMapping("/phone/send-otp")
    public Map<String, Object> sendPhoneBindOtp(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody SendPhoneOtpRequest req
    ) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (user.getPhone() != null && !user.getPhone().startsWith("email:")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手机号已绑定，不可修改");
        }
        if (!StringUtils.hasText(req.phone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已被其他账号使用");
        }

        String devCode = otpService.sendOtp(req.phone(), OtpEntity.Purpose.BIND_PHONE);
        Map<String, Object> res = new HashMap<>();
        res.put("message", "验证码已发送");
        if (devCode != null) res.put("devCode", devCode);
        return res;
    }

    @PostMapping("/phone/verify")
    public ProfileDto verifyPhoneBind(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody VerifyPhoneRequest req
    ) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (user.getPhone() != null && !user.getPhone().startsWith("email:")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手机号已绑定，不可修改");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已被其他账号使用");
        }

        otpService.verifyOtp(req.phone(), req.code(), OtpEntity.Purpose.BIND_PHONE);

        user.setPhone(req.phone());
        userRepository.save(user);
        return toDto(user);
    }

    @PostMapping("/email/send-otp")
    public Map<String, Object> sendEmailBindOtp(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody Map<String, String> body
    ) {
        String email = body.get("email");
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的邮箱地址");
        }
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (StringUtils.hasText(user.getEmail()) && user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已绑定");
        }
        if (userRepository.findByEmail(email).filter(u -> !u.getId().equals(user.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已被其他账号使用");
        }
        String devCode = otpService.sendEmailOtp(email, OtpEntity.Purpose.BIND_EMAIL);
        Map<String, Object> res = new HashMap<>();
        res.put("message", "验证码已发送至 " + email);
        if (devCode != null) res.put("devCode", devCode);
        return res;
    }

    @PostMapping("/email/verify")
    public ProfileDto verifyEmailBind(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody Map<String, String> body
    ) {
        String email = body.get("email");
        String code  = body.get("code");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数缺失");
        }
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (userRepository.findByEmail(email).filter(u -> !u.getId().equals(user.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已被其他账号使用");
        }
        otpService.verifyOtp(email, code, OtpEntity.Purpose.BIND_EMAIL);
        user.setEmail(email);
        user.setEmailVerified(true);
        userRepository.save(user);
        return toDto(user);
    }

    @PostMapping("/setup-credentials")
    public ResponseEntity<Map<String, String>> setupCredentials(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody SetupCredentialsRequest req
    ) {
        UserEntity user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (StringUtils.hasText(req.username())) {
            if (user.getUsername() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已设置，不可修改");
            }
            if (userRepository.existsByUsername(req.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被使用");
            }
            user.setUsername(req.username());
        }

        if (StringUtils.hasText(req.password())) {
            if (req.password().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少需要8位");
            }
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "账号信息已设置"));
    }

    private ProfileDto toDto(UserEntity u) {
        String displayName = u.getShippingFullName();
        if (displayName == null || displayName.isBlank()) displayName = u.getDisplayName();
        if (displayName == null || displayName.isBlank()) displayName = "紫来淘客" + String.format("%06d", u.getId());

        // If no addressesJson yet but flat columns exist, auto-migrate to JSON
        String addressesJson = u.getAddressesJson();
        if (addressesJson == null && u.getShippingCountry() != null && !u.getShippingCountry().isBlank()) {
            try {
                Map<String, Object> addr = new LinkedHashMap<>();
                addr.put("fullName",   u.getShippingFullName()   != null ? u.getShippingFullName()   : "");
                addr.put("phone",      u.getShippingPhone()      != null ? u.getShippingPhone()      : "");
                addr.put("street",     u.getShippingStreet()     != null ? u.getShippingStreet()     : "");
                addr.put("city",       u.getShippingCity()       != null ? u.getShippingCity()       : "");
                addr.put("province",   u.getShippingProvince()   != null ? u.getShippingProvince()   : "");
                addr.put("postalCode", u.getShippingPostalCode() != null ? u.getShippingPostalCode() : "");
                addr.put("country",    u.getShippingCountry());
                addressesJson = MAPPER.writeValueAsString(Map.of(u.getShippingCountry(), addr));
                u.setAddressesJson(addressesJson);
                userRepository.save(u);
            } catch (Exception ignored) {}
        }

        return new ProfileDto(
                u.getId(),
                u.getUsername(),
                displayName,
                u.getEmail(),
                u.getPhone(),
                u.getShippingFullName(),
                u.getShippingPhone(),
                u.getShippingStreet(),
                u.getShippingCity(),
                u.getShippingProvince(),
                u.getShippingPostalCode(),
                u.getShippingCountry(),
                u.getCloudId(),
                u.getPoints(),
                addressesJson
        );
    }
}
