package com.zilai.zilaibuy.scheduler;

import com.zilai.zilaibuy.service.RakutenBookSyncService;
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
    private final RakutenBookSyncService bookSyncService;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Toronto")
    public void nightlySync() {
        log.info("[RakutenSyncScheduler] Starting daily Ichiba sync...");

        String[] keywords = {
            "Fashion", "iphone", "DEAL", "nintendo", "ps5", "airpods",
            "Best Sellers", "Electronics", "Baby", "Snacks", "Skincare", "Anime"
        };
        int pages = 2;
        int hitsPerPage = 30;

        LocalDateTime syncStartTime = LocalDateTime.now();
        boolean allSucceeded = true;

        for (String keyword : keywords) {
            try {
                int count = syncService.syncKeyword(keyword, pages, hitsPerPage);
                log.info("[RakutenSyncScheduler] Synced {} items for keyword '{}'", count, keyword);
                Thread.sleep(10_000);
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

        if (allSucceeded) {
            int deactivated = syncService.deactivateStaleItems(syncStartTime);
            log.info("[RakutenSyncScheduler] Deactivated {} stale items", deactivated);
        } else {
            log.warn("[RakutenSyncScheduler] Skipping stale deactivation because some keywords failed");
        }
    }

    // Books sync runs 30 minutes after Ichiba to avoid concurrent API pressure
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Toronto")
    public void nightlyBooksSync() {
        log.info("[RakutenSyncScheduler] Starting daily Books sync...");

        String[] bookKeywords = {
            // 人气漫画
            "ワンピース", "鬼滅の刃", "進撃の巨人", "ドラゴンボール", "NARUTO",
            "名探偵コナン", "呪術廻戦", "チェンソーマン",
            // 人气小说/文学
            "村上春樹", "東野圭吾", "百年の孤独",
            // 实用/商业
            "ビジネス 入門", "プログラミング Python", "料理 レシピ",
            // 童书/绘本
            "絵本 人気", "はらぺこあおむし"
        };
        int pages = 2;
        int hitsPerPage = 30;

        LocalDateTime syncStartTime = LocalDateTime.now();
        boolean allSucceeded = true;

        for (String keyword : bookKeywords) {
            try {
                int count = bookSyncService.syncKeyword(keyword, pages, hitsPerPage);
                log.info("[RakutenSyncScheduler] Synced {} books for keyword '{}'", count, keyword);
                Thread.sleep(10_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[RakutenSyncScheduler] Books sync interrupted");
                allSucceeded = false;
                break;
            } catch (Exception e) {
                log.error("[RakutenSyncScheduler] Error syncing books keyword '{}'", keyword, e);
                allSucceeded = false;
            }
        }

        if (allSucceeded) {
            int deactivated = bookSyncService.deactivateStaleItems(syncStartTime);
            log.info("[RakutenSyncScheduler] Deactivated {} stale books", deactivated);
        } else {
            log.warn("[RakutenSyncScheduler] Skipping books stale deactivation because some keywords failed");
        }
    }
}
