package com.zilai.zilaibuy.api;

import com.zilai.zilaibuy.rakuten.RakutenClient;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import com.zilai.zilaibuy.scheduler.RakutenSyncScheduler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/rakuten")
public class RakutenController {

    private final RakutenClient rakutenClient;
    private final RakutenSyncScheduler syncScheduler;

    public RakutenController(RakutenClient rakutenClient, RakutenSyncScheduler syncScheduler) {
        this.rakutenClient = rakutenClient;
        this.syncScheduler = syncScheduler;
    }

    @GetMapping("/search")
    public RakutenIchibaSearchResponse search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") Integer hits
    ) {
        return rakutenClient.search(keyword, hits);
    }

    @PostMapping("/sync")
    public String sync() {
        syncScheduler.nightlySync();
        return "Sync completed";
    }
}
