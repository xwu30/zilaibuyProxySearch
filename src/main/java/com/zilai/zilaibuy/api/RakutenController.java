package com.zilai.zilaibuy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zilai.zilaibuy.rakuten.RakutenClient;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/rakuten")
public class RakutenController {

    private final RakutenClient rakutenClient;

    public RakutenController(RakutenClient rakutenClient) {
        this.rakutenClient = rakutenClient;
    }

    @GetMapping("/search")
    public RakutenIchibaSearchResponse search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") Integer hits
    ) {
        return rakutenClient.search(keyword, hits);
    }
}
