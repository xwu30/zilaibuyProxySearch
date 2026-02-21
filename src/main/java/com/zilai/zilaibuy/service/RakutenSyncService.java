package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.RakutenFetchJobEntity;
import com.zilai.zilaibuy.entity.RakutenItemEntity;
import com.zilai.zilaibuy.repository.RakutenFetchJobRepository;
import com.zilai.zilaibuy.repository.RakutenItemRepository;
import com.zilai.zilaibuy.rakuten.RakutenClient;
import com.zilai.zilaibuy.rakuten.RakutenMapper;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


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

        int saved = 0;

        try {
            for (int page = 1; page <= pages; page++) {
                RakutenIchibaSearchResponse resp = rakutenClient.search(keyword, page, hitsPerPage);
                List<RakutenIchibaSearchResponse.ItemWrapper> items = resp.items();
                if (items == null || items.isEmpty()) {
                    log.info("[RakutenSyncService] keyword={}, page={} no items", keyword, page);
                    continue;
                }

                // Collect valid items for batch translation
                List<RakutenIchibaSearchResponse.ItemWrapper> validItems = new ArrayList<>();
                for (RakutenIchibaSearchResponse.ItemWrapper w : items) {
                    if (w != null && w.item() != null) validItems.add(w);
                }

                // Batch translate itemName and catchCopy (ja -> zh-CN)
                List<String> itemNames = new ArrayList<>();
                List<String> catchCopies = new ArrayList<>();
                for (RakutenIchibaSearchResponse.ItemWrapper w : validItems) {
                    itemNames.add(w.item().itemName());
                    catchCopies.add(w.item().catchcopy());
                }

                List<String> translatedNames = translateService.translateBatch(itemNames, "ja", "zh-CN");
                List<String> translatedCopies = translateService.translateBatch(catchCopies, "ja", "zh-CN");

                // Map to entities with translations
                List<RakutenItemEntity> entities = new ArrayList<>();
                for (int i = 0; i < validItems.size(); i++) {
                    RakutenIchibaSearchResponse.Item item = validItems.get(i).item();
                    String nameZh = translatedNames.get(i);
                    String copyZh = translatedCopies.get(i);

                    RakutenItemEntity existing = itemRepo.findByItemCode(item.itemCode()).orElse(null);
                    if (existing != null) {
                        mapper.updateEntity(existing, item, keyword, nameZh, copyZh);
                        entities.add(existing);
                    } else {
                        entities.add(mapper.toEntity(item, keyword, nameZh, copyZh));
                    }
                }

                itemRepo.saveAll(entities);
                saved += entities.size();
                log.info("[RakutenSyncService] keyword={}, page={} saved={}", keyword, page, entities.size());
            }

            job.setStatus("SUCCESS");
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1024)) : "Unknown error");
            throw e;
        } finally {
            job.setItemsSaved(saved);
            job.setFinishedAt(LocalDateTime.now());
            fetchJobRepo.save(job);
        }

        return saved;
    }
}
