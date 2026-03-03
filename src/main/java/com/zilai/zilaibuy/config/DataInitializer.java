package com.zilai.zilaibuy.config;

import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_PHONE = "+16479852487";
    private static final String ADMIN_PASSWORD = "qwer1234";

    private static final String XWU30_PHONE = "+10000000001";
    private static final String XWU30_PASSWORD = "Qwer1234!";

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        UserEntity admin = userRepository.findByPhone(ADMIN_PHONE).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setPhone(ADMIN_PHONE);
            u.setUsername("admin");
            u.setDisplayName("管理员");
            u.setActive(true);
            log.info("Admin user created: phone={}", ADMIN_PHONE);
            return u;
        });

        // Always enforce correct role and password
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(UserEntity.Role.ADMIN);
        admin.setLocked(false);
        admin.setLoginFailCount(0);
        userRepository.save(admin);
        log.info("Admin user ready: phone={}", ADMIN_PHONE);

        UserEntity xwu30 = userRepository.findByUsername("xwu30").orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setPhone(XWU30_PHONE);
            u.setUsername("xwu30");
            u.setDisplayName("xwu30");
            u.setActive(true);
            log.info("xwu30 user created");
            return u;
        });

        xwu30.setPasswordHash(passwordEncoder.encode(XWU30_PASSWORD));
        xwu30.setRole(UserEntity.Role.ADMIN);
        xwu30.setLocked(false);
        xwu30.setLoginFailCount(0);
        userRepository.save(xwu30);
        log.info("xwu30 user ready");
    }
}
