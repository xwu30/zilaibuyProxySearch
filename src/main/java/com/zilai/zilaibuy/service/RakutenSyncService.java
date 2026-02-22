package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.RakutenFetchJobEntity;
import com.zilai.zilaibuy.entity.RakutenItemEntity;
import com.zilai.zilaibuy.repository.RakutenFetchJobRepository;
import com.zilai.zilaibuy.repository.RakutenItemRepository;
import com.zilai.zilaibuy.rakuten.RakutenClient;
import com.zilai.zilaibuy.rakuten.RakutenMapper;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Service
@RequiredArgsConstructor
public class RakutenSyncService {
    private static final Logger log = LoggerFactory.getLogger(RakutenSyncService.class);
    private final RakutenClient rakutenClient;
    private final RakutenItemRepository itemRepo;
    private final RakutenFetchJobRepository fetchJobRepo;
    private final RakutenMapper mapper;
    private final GoogleTranslateService translateService;

    public int syncKeyword(String keyword, int pages, int hitsPerPage) {
        if (pages <= 0) pages = 1;
        if (hitsPerPage <= 0) hitsPerPage = 30;

        RakutenFetchJobEntity job = new RakutenFetchJobEntity();
        job.setKeyword(keyword);
        job.setPages(pages);
        job.setStartedAt(LocalDateTime.now());

        int totalNew = 0, totalUpdated = 0;

        try {
            for (int page = 1; page <= pages; page++) {
                RakutenIchibaSearchResponse resp = rakutenClient.search(keyword, page, hitsPerPage);
                List<RakutenIchibaSearchResponse.ItemWrapper> items = resp.items();
                if (items == null || items.isEmpty()) {
                    log.info("[RakutenSyncService] keyword={}, page={} no items", keyword, page);
                    continue;
                }

                // Collect valid items
                List<RakutenIchibaSearchResponse.ItemWrapper> validItems = new ArrayList<>();
                for (RakutenIchibaSearchResponse.ItemWrapper w : items) {
                    if (w != null && w.item() != null) validItems.add(w);
                }

                // Fix #1: Batch fetch existing items instead of N+1 queries
                List<String> codes = new ArrayList<>();
                for (RakutenIchibaSearchResponse.ItemWrapper w : validItems) {
                    codes.add(w.item().itemCode());
                }
                Map<String, RakutenItemEntity> existingMap = new HashMap<>();
                for (RakutenItemEntity e : itemRepo.findByItemCodeIn(codes)) {
                    existingMap.put(e.getItemCode(), e);
                }

                // Fix #2: Translate only items that need it (new items or items whose text changed)
                List<Integer> toTranslateIndices = new ArrayList<>();
                List<String> namesToTranslate = new ArrayList<>();
                List<String> copiesToTranslate = new ArrayList<>();

                for (int i = 0; i < validItems.size(); i++) {
                    RakutenIchibaSearchResponse.Item item = validItems.get(i).item();
                    RakutenItemEntity existing = existingMap.get(item.itemCode());
                    boolean needsTranslation = existing == null
                            || !Objects.equals(existing.getItemName(), item.itemName())
                            || !Objects.equals(existing.getCatchCopy(), item.catchcopy());
                    if (needsTranslation) {
                        toTranslateIndices.add(i);
                        namesToTranslate.add(item.itemName());
                        copiesToTranslate.add(item.catchcopy());
                    }
                }

                List<String> translatedNames = namesToTranslate.isEmpty()
                        ? List.of()
                        : translateService.translateBatch(namesToTranslate, "ja", "zh-CN");
                List<String> translatedCopies = copiesToTranslate.isEmpty()
                        ? List.of()
                        : translateService.translateBatch(copiesToTranslate, "ja", "zh-CN");

                Map<Integer, String[]> translationByIndex = new HashMap<>();
                for (int j = 0; j < toTranslateIndices.size(); j++) {
                    translationByIndex.put(toTranslateIndices.get(j),
                            new String[]{translatedNames.get(j), translatedCopies.get(j)});
                }

                // Build entities
                List<RakutenItemEntity> entities = new ArrayList<>();
                int pageNew = 0, pageUpdated = 0;
                for (int i = 0; i < validItems.size(); i++) {
                    RakutenIchibaSearchResponse.Item item = validItems.get(i).item();
                    RakutenItemEntity existing = existingMap.get(item.itemCode());
                    String[] translation = translationByIndex.get(i);

                    if (existing != null) {
                        // Fix #2: reuse stored translations when text hasn't changed
                        String nameZh = translation != null ? translation[0] : existing.getItemNameZh();
                        String copyZh = translation != null ? translation[1] : existing.getCatchCopyZh();
                        // Fix #3: keyword is NOT overwritten on update (kept from first sync)
                        mapper.updateEntity(existing, item, nameZh, copyZh);
                        entities.add(existing);
                        pageUpdated++;
                    } else {
                        String nameZh = translation != null ? translation[0] : null;
                        String copyZh = translation != null ? translation[1] : null;
                        entities.add(mapper.toEntity(item, keyword, nameZh, copyZh));
                        pageNew++;
                    }
                }

                itemRepo.saveAll(entities);
                totalNew += pageNew;
                totalUpdated += pageUpdated;
                log.info("[RakutenSyncService] keyword={}, page={} new={} updated={}", keyword, page, pageNew, pageUpdated);
            }

            job.setStatus("SUCCESS");
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1024)) : "Unknown error");
            throw e;
        } finally {
            // Fix #5: track new vs updated separately
            job.setItemsSaved(totalNew);
            job.setItemsUpdated(totalUpdated);
            job.setFinishedAt(LocalDateTime.now());
            fetchJobRepo.save(job);
        }

        return totalNew + totalUpdated;
    }

    // Fix #4: deactivate items not seen in the current sync run
    public int deactivateStaleItems(LocalDateTime syncStartTime) {
        int count = itemRepo.deactivateStaleItems(syncStartTime);
        log.info("[RakutenSyncService] Deactivated {} stale items not seen since {}", count, syncStartTime);
        return count;
    }
}
