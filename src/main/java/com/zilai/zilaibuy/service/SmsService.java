package com.zilai.zilaibuy.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.from-phone:}")
    private String fromPhone;

    private boolean twilioEnabled;

    @PostConstruct
    public void init() {
        twilioEnabled = StringUtils.hasText(accountSid) && StringUtils.hasText(authToken)
                && StringUtils.hasText(fromPhone);
        if (twilioEnabled) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS service initialized");
        } else {
            log.warn("Twilio credentials not configured — SMS will be logged only");
        }
    }

    /**
     * Returns true if SMS was actually sent, false if logged only (dev mode).
     */
    public boolean sendOtp(String toPhone, String code) {
        String body = "【ZilaiBuy】Your verification code is: " + code + ". Valid for 5 minutes.";
        if (twilioEnabled) {
            try {
                Message.creator(new PhoneNumber(toPhone), new PhoneNumber(fromPhone), body).create();
                log.info("OTP SMS sent to {}", toPhone);
                return true;
            } catch (Exception e) {
                log.warn("Twilio send failed ({}), falling back to dev log", e.getMessage());
            }
        }
        log.info("[DEV] OTP for {}: {}", toPhone, code);
        return false;
    }
}
