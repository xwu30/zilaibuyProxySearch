package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.OtpEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final SmsService smsService;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /**
     * Sends OTP. Returns the code itself when SMS was NOT actually delivered
     * (Twilio unavailable / dev mode), so callers can surface it in the response
     * for local testing. Returns null when SMS was delivered normally.
     */
    @Transactional
    public String sendOtp(String phone, OtpEntity.Purpose purpose) {
        // Rate limiting: max 1 per 60s
        long last60s = otpRepository.countByPhoneAndCreatedAtAfter(phone,
                LocalDateTime.now().minusSeconds(60));
        if (last60s > 0) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "请等待60秒后再次发送");
        }

        // Rate limiting: max 5 per hour
        long lastHour = otpRepository.countByPhoneAndCreatedAtAfter(phone,
                LocalDateTime.now().minusHours(1));
        if (lastHour >= 5) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "1小时内发送次数已达上限");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        OtpEntity otp = new OtpEntity();
        otp.setPhone(phone);
        otp.setCode(code);
        otp.setPurpose(purpose);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otp);

        boolean smsSent = smsService.sendOtp(phone, code);
        return smsSent ? null : code;
    }

    /**
     * Sends OTP via email. Returns the code when email was NOT delivered (dev mode), null otherwise.
     */
    @Transactional
    public String sendEmailOtp(String email, OtpEntity.Purpose purpose) {
        long last60s = otpRepository.countByPhoneAndCreatedAtAfter(email,
                LocalDateTime.now().minusSeconds(60));
        if (last60s > 0) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "请等待60秒后再次发送");
        }

        long lastHour = otpRepository.countByPhoneAndCreatedAtAfter(email,
                LocalDateTime.now().minusHours(1));
        if (lastHour >= 5) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "1小时内发送次数已达上限");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        OtpEntity otp = new OtpEntity();
        otp.setPhone(email);
        otp.setCode(code);
        otp.setPurpose(purpose);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otp);

        boolean sent = emailService.sendOtpEmail(email, code);
        return sent ? null : code;
    }

    @Transactional
    public void verifyOtp(String phone, String code, OtpEntity.Purpose purpose) {
        OtpEntity otp = otpRepository.findLatestValid(phone, purpose, LocalDateTime.now())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "验证码无效或已过期"));

        if (!otp.getCode().equals(code)) {
            otp.setFailAttempts(otp.getFailAttempts() + 1);
            if (otp.getFailAttempts() >= 5) {
                otp.setUsed(true);
            }
            otpRepository.save(otp);
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码错误");
        }

        otp.setUsed(true);
        otpRepository.save(otp);
    }
}
