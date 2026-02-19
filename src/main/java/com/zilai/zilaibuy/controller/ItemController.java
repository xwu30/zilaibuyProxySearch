package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.RakutenItemEntity;
import com.zilai.zilaibuy.service.RakutenSyncService;
import com.zilai.zilaibuy.repository.RakutenItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private static final Logger log = LoggerFactory.getLogger(ItemController.class);
    private final RakutenItemRepository itemRepo;
    private final RakutenSyncService syncService;

    public ItemController(RakutenItemRepository itemRepo,
                          RakutenSyncService syncService) {
        this.itemRepo = itemRepo;
        this.syncService = syncService;
    }
    // 搜索商品
    @GetMapping("/search")
    public Page<RakutenItemEntity> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size) {
        Page<RakutenItemEntity> result = itemRepo.search(keyword, PageRequest.of(page, size));
        log.info("[ItemController] search keyword={}, page={}, size={}, totalElements={}, totalPages={}, returnedItems={}",
                keyword, page, size, result.getTotalElements(), result.getTotalPages(), result.getNumberOfElements());
        result.getContent().forEach(item ->
                log.info("[ItemController]   item: code={}, name={}, price={}, shop={}, imageSmall={}, imageMedium={}",
                        item.getItemCode(), item.getItemName(), item.getItemPrice(),
                        item.getShopName(), item.getImageSmall(), item.getImageMedium()));
        return result;
    }

    // 直接触发同步任务（管理员端，便于调试）
    @PostMapping("/sync")
    public String syncKeyword(@RequestParam String keyword) {
        try {
            int count = syncService.syncKeyword(keyword, 1, 30);  // 可根据需要调整 pages 和 hitsPerPage
            return "Synced " + count + " items for keyword: " + keyword;
        } catch (Exception e) {
            return "Error syncing keyword " + keyword + ": " + e.getMessage();
        }
    }
}
