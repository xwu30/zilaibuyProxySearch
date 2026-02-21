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

    // 每天下午3点同步 Rakuten 数据
    @Scheduled(cron = "0 0 22 * * *", zone = "America/Toronto")
    public void nightlySync() {
        log.info("[RakutenSyncScheduler] Starting daily sync...");

        String[] keywords = {
            "Fashion", "iphone", "DEAL", "nintendo", "ps5", "airpods",
            "Best Sellers", "Electronics", "Baby", "Snacks", "Skincare", "Anime"
        };
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
