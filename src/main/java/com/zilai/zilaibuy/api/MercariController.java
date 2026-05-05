package com.zilai.zilaibuy.api;

import com.zilai.zilaibuy.mercari.MercariClient;
import com.zilai.zilaibuy.mercari.dto.MercariSearchResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mercari")
public class MercariController {

    private final MercariClient mercariClient;

    public MercariController(MercariClient mercariClient) {
        this.mercariClient = mercariClient;
    }

    @GetMapping("/search")
    public MercariSearchResponse search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) String pageToken
    ) {
        return mercariClient.search(keyword, limit, pageToken);
    }
}
