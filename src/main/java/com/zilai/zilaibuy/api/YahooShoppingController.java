package com.zilai.zilaibuy.api;

import com.zilai.zilaibuy.yahoo.YahooShoppingClient;
import com.zilai.zilaibuy.yahoo.dto.YahooShoppingSearchResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/yahoo")
public class YahooShoppingController {

    private final YahooShoppingClient yahooShoppingClient;

    public YahooShoppingController(YahooShoppingClient yahooShoppingClient) {
        this.yahooShoppingClient = yahooShoppingClient;
    }

    @GetMapping("/shopping/search")
    public YahooShoppingSearchResponse search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int hits
    ) {
        return yahooShoppingClient.search(keyword, page, hits);
    }
}
