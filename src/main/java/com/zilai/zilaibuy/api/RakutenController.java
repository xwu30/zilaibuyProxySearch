package com.zilai.zilaibuy.api;

import com.zilai.zilaibuy.rakuten.RakutenBooksClient;
import com.zilai.zilaibuy.rakuten.RakutenClient;
import com.zilai.zilaibuy.rakuten.dto.RakutenBooksSearchResponse;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import com.zilai.zilaibuy.scheduler.RakutenSyncScheduler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/rakuten")
public class RakutenController {

    private final RakutenClient rakutenClient;
    private final RakutenBooksClient rakutenBooksClient;
    private final RakutenSyncScheduler syncScheduler;

    public RakutenController(RakutenClient rakutenClient, RakutenBooksClient rakutenBooksClient, RakutenSyncScheduler syncScheduler) {
        this.rakutenClient = rakutenClient;
        this.rakutenBooksClient = rakutenBooksClient;
        this.syncScheduler = syncScheduler;
    }

    @GetMapping("/search")
    public RakutenIchibaSearchResponse search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") Integer hits
    ) {
        return rakutenClient.search(keyword, hits);
    }

    @GetMapping("/books/search")
    public RakutenBooksSearchResponse booksSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer hits
    ) {
        return rakutenBooksClient.search(keyword, page, hits);
    }

    @PostMapping("/sync")
    public String sync() {
        syncScheduler.nightlySync();
        return "Sync completed";
    }
}
