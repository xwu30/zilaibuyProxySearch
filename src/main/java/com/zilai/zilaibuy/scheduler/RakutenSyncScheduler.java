package com.zilai.zilaibuy.scheduler;

import com.zilai.zilaibuy.service.RakutenSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RakutenSyncScheduler {

    private final RakutenSyncService syncService;

    // 每天凌晨3点同步 Rakuten 数据
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Toronto")
    public void nightlySync() {
        log.info("[RakutenSyncScheduler] Starting nightly sync...");

        // 这里设置你希望同步的关键词
        String[] keywords = {"iphone", "nintendo", "ps5", "airpods"};
        int pages = 2;
        int hitsPerPage = 30;

        for (String keyword : keywords) {
            try {
                int count = syncService.syncKeyword(keyword, pages, hitsPerPage);
                log.info("[RakutenSyncScheduler] Synced {} items for keyword {}", count, keyword);
            } catch (Exception e) {
                log.error("[RakutenSyncScheduler] Error syncing keyword {}", keyword, e);
            }
        }
    }
}
