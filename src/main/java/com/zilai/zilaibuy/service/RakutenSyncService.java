package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.RakutenItemEntity;
import com.zilai.zilaibuy.repository.RakutenItemRepository;
import com.zilai.zilaibuy.rakuten.RakutenClient;
import com.zilai.zilaibuy.rakuten.RakutenMapper;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class RakutenSyncService {
    private static final Logger log = LoggerFactory.getLogger(RakutenSyncService.class);
    private final RakutenClient rakutenClient;
    private final RakutenItemRepository itemRepo;
    private final RakutenMapper mapper;

    public int syncKeyword(String keyword, int pages, int hitsPerPage) {
        if (pages <= 0) pages = 1;
        if (hitsPerPage <= 0) hitsPerPage = 30;

        int saved = 0;

        for (int page = 1; page <= pages; page++) {
            RakutenIchibaSearchResponse resp = rakutenClient.search(keyword, page, hitsPerPage);
            List<RakutenIchibaSearchResponse.ItemWrapper> items = resp.items();
            if (items == null || items.isEmpty()) {
                log.info("[RakutenSyncService] keyword={}, page={} no items", keyword, page);
                continue;
            }

            List<RakutenItemEntity> entities = new ArrayList<>();
            for (RakutenIchibaSearchResponse.ItemWrapper w : items) {
                if (w == null || w.item() == null) continue;
                entities.add(mapper.toEntity(w.item(), keyword));
            }

            itemRepo.saveAll(entities);
            saved += entities.size();
            log.info("[RakutenSyncService] keyword={}, page={} saved={}", keyword, page, entities.size());
        }

        return saved;
    }
}
