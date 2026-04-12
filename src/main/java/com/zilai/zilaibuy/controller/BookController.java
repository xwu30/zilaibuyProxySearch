package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.RakutenBookEntity;
import com.zilai.zilaibuy.repository.RakutenBookRepository;
import com.zilai.zilaibuy.service.RakutenBookSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final RakutenBookRepository bookRepo;
    private final RakutenBookSyncService bookSyncService;

    @GetMapping("/search")
    public Page<RakutenBookEntity> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Page<RakutenBookEntity> result = hasKeyword
                ? bookRepo.search(keyword, PageRequest.of(page, size))
                : bookRepo.searchRandom(PageRequest.of(page, size));
        log.info("[BookController] search keyword={}, page={}, size={}, total={}", keyword, page, size, result.getTotalElements());
        return result;
    }

    @PostMapping("/sync")
    public String syncKeyword(@RequestParam String keyword,
                              @RequestParam(defaultValue = "2") int pages,
                              @RequestParam(defaultValue = "30") int hits) {
        try {
            int count = bookSyncService.syncKeyword(keyword, pages, hits);
            return "Synced " + count + " books for keyword: " + keyword;
        } catch (Exception e) {
            return "Error syncing keyword " + keyword + ": " + e.getMessage();
        }
    }
}
