package com.zilai.zilaibuy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public boolean sendOtpEmail(String toEmail, String code) {
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Email OTP for {}: {}", toEmail, code);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】您的注册验证码");
            msg.setText("您好！\n\n您的注册验证码为：" + code + "\n\n验证码5分钟内有效，请勿泄露给他人。\n\n如非本人操作，请忽略此邮件。\n\n— ZilaiBuy 团队");
            mailSender.send(msg);
            log.info("OTP email sent to {}", toEmail);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send OTP email to {} ({})", toEmail, e.getMessage());
            return false;
        }
    }

    public void sendConfirmationEmail(String toEmail, String token) {
        String link = baseUrl + "/api/auth/confirm-email?token=" + token;
        if (!StringUtils.hasText(fromEmail)) {
            log.info("[DEV] Email confirmation link for {}: {}", toEmail, link);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(toEmail);
            msg.setSubject("【ZilaiBuy】请确认您的邮箱");
            msg.setText("您好！\n\n请点击以下链接完成邮箱验证（24小时内有效）：\n\n" + link + "\n\n如非本人操作，请忽略此邮件。\n\n— ZilaiBuy 团队");
            mailSender.send(msg);
            log.info("Confirmation email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send email to {} ({}), link: {}", toEmail, e.getMessage(), link);
        }
    }
}
