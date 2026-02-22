package com.zilai.zilaibuy.scheduler;

import com.zilai.zilaibuy.service.RakutenSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RakutenSyncScheduler {

    private final RakutenSyncService syncService;

    @Scheduled(cron = "0 0 22 * * *", zone = "America/Toronto")
    public void nightlySync() {
        log.info("[RakutenSyncScheduler] Starting daily sync...");

        String[] keywords = {
            "Fashion", "iphone", "DEAL", "nintendo", "ps5", "airpods",
            "Best Sellers", "Electronics", "Baby", "Snacks", "Skincare", "Anime"
        };
        int pages = 2;
        int hitsPerPage = 30;

        // Fix #4: record start time so we can deactivate items not seen in this run
        LocalDateTime syncStartTime = LocalDateTime.now();
        boolean allSucceeded = true;

        for (String keyword : keywords) {
            try {
                int count = syncService.syncKeyword(keyword, pages, hitsPerPage);
                log.info("[RakutenSyncScheduler] Synced {} items for keyword '{}'", count, keyword);
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[RakutenSyncScheduler] Sync interrupted");
                allSucceeded = false;
                break;
            } catch (Exception e) {
                log.error("[RakutenSyncScheduler] Error syncing keyword '{}'", keyword, e);
                allSucceeded = false;
            }
        }

        // Fix #4: only deactivate stale items if all keywords completed successfully
        // to avoid incorrectly deactivating items whose keyword failed due to API errors
        if (allSucceeded) {
            int deactivated = syncService.deactivateStaleItems(syncStartTime);
            log.info("[RakutenSyncScheduler] Deactivated {} stale items", deactivated);
        } else {
            log.warn("[RakutenSyncScheduler] Skipping stale deactivation because some keywords failed");
        }
    }
}
