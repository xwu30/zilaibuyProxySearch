package com.zilai.zilaibuy.config;

import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.RakutenBookRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.service.RakutenBookSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RakutenBookRepository bookRepository;
    private final RakutenBookSyncService bookSyncService;

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

        // Pre-warm Books data if table is empty
        long bookCount = bookRepository.count();
        if (bookCount == 0) {
            log.info("[DataInitializer] Books table empty, triggering initial sync in background...");
            initialBooksSync();
        } else {
            log.info("[DataInitializer] Books table has {} records, skipping initial sync", bookCount);
        }
    }

    @Async
    public void initialBooksSync() {
        String[] keywords = {
            "ワンピース", "鬼滅の刃", "進撃の巨人", "ドラゴンボール", "NARUTO",
            "名探偵コナン", "呪術廻戦", "チェンソーマン",
            "村上春樹", "東野圭吾",
            "ビジネス 入門", "プログラミング Python",
            "料理 レシピ", "絵本 人気"
        };
        for (String keyword : keywords) {
            try {
                int count = bookSyncService.syncKeyword(keyword, 2, 30);
                log.info("[DataInitializer] Initial books sync: {} books for '{}'", count, keyword);
                Thread.sleep(8_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[DataInitializer] Failed to sync books keyword '{}': {}", keyword, e.getMessage());
            }
        }
        log.info("[DataInitializer] Initial books sync complete. Total: {} books", bookRepository.count());
    }
}
