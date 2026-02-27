package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.user.ProfileDto;
import com.zilai.zilaibuy.dto.user.SendPhoneOtpRequest;
import com.zilai.zilaibuy.dto.user.UpdateProfileRequest;
import com.zilai.zilaibuy.dto.user.VerifyPhoneRequest;
import com.zilai.zilaibuy.entity.OtpEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final OtpService otpService;

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

    private ProfileDto toDto(UserEntity u) {
        String displayName = u.getShippingFullName();
        if (displayName == null || displayName.isBlank()) displayName = u.getDisplayName();
        if (displayName == null || displayName.isBlank()) displayName = "紫来淘客" + String.format("%06d", u.getId());
        return new ProfileDto(
                u.getId(),
                displayName,
                u.getEmail(),
                u.getPhone(),
                u.getShippingFullName(),
                u.getShippingPhone(),
                u.getShippingStreet(),
                u.getShippingCity(),
                u.getShippingProvince(),
                u.getShippingPostalCode()
        );
    }
}
