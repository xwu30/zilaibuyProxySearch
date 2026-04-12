package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.RakutenBookEntity;
import com.zilai.zilaibuy.entity.RakutenFetchJobEntity;
import com.zilai.zilaibuy.rakuten.RakutenBooksClient;
import com.zilai.zilaibuy.rakuten.dto.RakutenBooksSearchResponse;
import com.zilai.zilaibuy.repository.RakutenBookRepository;
import com.zilai.zilaibuy.repository.RakutenFetchJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RakutenBookSyncService {

    private final RakutenBooksClient booksClient;
    private final RakutenBookRepository bookRepo;
    private final RakutenFetchJobRepository fetchJobRepo;
    private final GoogleTranslateService translateService;

    public int syncKeyword(String keyword, int pages, int hitsPerPage) throws InterruptedException {
        if (pages <= 0) pages = 1;
        if (hitsPerPage <= 0) hitsPerPage = 30;

        RakutenFetchJobEntity job = new RakutenFetchJobEntity();
        job.setKeyword("books:" + keyword);
        job.setPages(pages);
        job.setStartedAt(LocalDateTime.now());

        int totalNew = 0, totalUpdated = 0;

        try {
            for (int page = 1; page <= pages; page++) {
                if (page > 1) Thread.sleep(2000);

                RakutenBooksSearchResponse resp = searchWithRetry(keyword, page, hitsPerPage);
                List<RakutenBooksSearchResponse.ItemWrapper> wrappers = resp.items();
                if (wrappers == null || wrappers.isEmpty()) {
                    log.info("[RakutenBookSyncService] keyword={}, page={} no items", keyword, page);
                    continue;
                }

                // Filter valid items with isbn
                List<RakutenBooksSearchResponse.Item> valid = new ArrayList<>();
                for (RakutenBooksSearchResponse.ItemWrapper w : wrappers) {
                    if (w != null && w.item() != null && w.item().isbn() != null && !w.item().isbn().isBlank()) {
                        valid.add(w.item());
                    }
                }

                // Batch fetch existing
                List<String> isbns = new ArrayList<>();
                for (RakutenBooksSearchResponse.Item item : valid) isbns.add(item.isbn());
                Map<String, RakutenBookEntity> existingMap = new HashMap<>();
                for (RakutenBookEntity e : bookRepo.findByIsbnIn(isbns)) existingMap.put(e.getIsbn(), e);

                // Translate only titles that need it
                List<Integer> toTranslateIdx = new ArrayList<>();
                List<String> titlesToTranslate = new ArrayList<>();
                for (int i = 0; i < valid.size(); i++) {
                    RakutenBooksSearchResponse.Item item = valid.get(i);
                    RakutenBookEntity existing = existingMap.get(item.isbn());
                    boolean needsTranslation = existing == null
                            || !Objects.equals(existing.getTitle(), item.title());
                    if (needsTranslation) {
                        toTranslateIdx.add(i);
                        titlesToTranslate.add(item.title());
                    }
                }

                List<String> translatedTitles = titlesToTranslate.isEmpty()
                        ? List.of()
                        : translateService.translateBatch(titlesToTranslate, "ja", "zh-CN");

                Map<Integer, String> translationByIdx = new HashMap<>();
                for (int j = 0; j < toTranslateIdx.size(); j++) {
                    translationByIdx.put(toTranslateIdx.get(j), translatedTitles.get(j));
                }

                // Build entities
                List<RakutenBookEntity> entities = new ArrayList<>();
                int pageNew = 0, pageUpdated = 0;
                for (int i = 0; i < valid.size(); i++) {
                    RakutenBooksSearchResponse.Item item = valid.get(i);
                    RakutenBookEntity existing = existingMap.get(item.isbn());
                    String titleZh = translationByIdx.get(i);

                    if (existing != null) {
                        String usedTitleZh = titleZh != null ? titleZh : existing.getTitleZh();
                        updateEntity(existing, item, usedTitleZh);
                        entities.add(existing);
                        pageUpdated++;
                    } else {
                        entities.add(toEntity(item, keyword, titleZh));
                        pageNew++;
                    }
                }

                bookRepo.saveAll(entities);
                totalNew += pageNew;
                totalUpdated += pageUpdated;
                log.info("[RakutenBookSyncService] keyword={}, page={} new={} updated={}", keyword, page, pageNew, pageUpdated);
            }
            job.setStatus("SUCCESS");
        } catch (Exception e) {
            job.setStatus("FAILED");
            String msg = e.getMessage();
            job.setErrorMessage(msg != null ? msg.substring(0, Math.min(msg.length(), 1024)) : "Unknown error");
            throw e;
        } finally {
            job.setItemsSaved(totalNew);
            job.setItemsUpdated(totalUpdated);
            job.setFinishedAt(LocalDateTime.now());
            fetchJobRepo.save(job);
        }

        return totalNew + totalUpdated;
    }

    public int deactivateStaleItems(LocalDateTime threshold) {
        int count = bookRepo.deactivateStaleItems(threshold);
        log.info("[RakutenBookSyncService] Deactivated {} stale books not seen since {}", count, threshold);
        return count;
    }

    private RakutenBooksSearchResponse searchWithRetry(String keyword, int page, int hits) throws InterruptedException {
        int[] retryDelaysMs = {30_000, 60_000};
        for (int attempt = 0; ; attempt++) {
            try {
                return booksClient.search(keyword, page, hits);
            } catch (Exception e) {
                boolean is429 = e.getMessage() != null && e.getMessage().contains("429");
                if (!is429 || attempt >= retryDelaysMs.length) throw e;
                log.warn("[RakutenBookSyncService] 429 for keyword={} page={}, waiting {}s before retry {}",
                        keyword, page, retryDelaysMs[attempt] / 1000, attempt + 1);
                Thread.sleep(retryDelaysMs[attempt]);
            }
        }
    }

    private RakutenBookEntity toEntity(RakutenBooksSearchResponse.Item item, String keyword, String titleZh) {
        RakutenBookEntity e = new RakutenBookEntity();
        e.setKeyword(keyword);
        e.setIsbn(item.isbn());
        e.setTitle(item.title());
        e.setTitleZh(titleZh);
        e.setAuthor(item.author());
        e.setPublisherName(item.publisherName());
        e.setSalesDate(item.salesDate());
        e.setItemPrice(item.itemPrice());
        e.setItemUrl(item.itemUrl());
        e.setLargeImageUrl(item.largeImageUrl());
        e.setMediumImageUrl(item.mediumImageUrl());
        e.setSmallImageUrl(item.smallImageUrl());
        e.setReviewCount(item.reviewCount());
        e.setReviewAverage(parseAvg(item.reviewAverage()));
        e.setBooksGenreId(item.booksGenreId());
        LocalDateTime now = LocalDateTime.now();
        e.setSyncedAt(now);
        e.setLastFetchedAt(now);
        e.setIsActive(true);
        return e;
    }

    private void updateEntity(RakutenBookEntity e, RakutenBooksSearchResponse.Item item, String titleZh) {
        e.setTitle(item.title());
        e.setTitleZh(titleZh);
        e.setAuthor(item.author());
        e.setPublisherName(item.publisherName());
        e.setSalesDate(item.salesDate());
        e.setItemPrice(item.itemPrice());
        e.setItemUrl(item.itemUrl());
        e.setLargeImageUrl(item.largeImageUrl());
        e.setMediumImageUrl(item.mediumImageUrl());
        e.setSmallImageUrl(item.smallImageUrl());
        e.setReviewCount(item.reviewCount());
        e.setReviewAverage(parseAvg(item.reviewAverage()));
        e.setBooksGenreId(item.booksGenreId());
        e.setLastFetchedAt(LocalDateTime.now());
        e.setIsActive(true);
    }

    private BigDecimal parseAvg(String avg) {
        if (avg == null || avg.isBlank() || "0.0".equals(avg)) return null;
        try { return new BigDecimal(avg); } catch (Exception ex) { return null; }
    }
}
