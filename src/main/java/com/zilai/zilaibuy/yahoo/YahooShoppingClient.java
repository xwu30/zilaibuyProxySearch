package com.zilai.zilaibuy.yahoo;

import com.zilai.zilaibuy.yahoo.dto.YahooShoppingSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class YahooShoppingClient {

    private static final String BASE_URL = "https://shopping.yahooapis.jp/ShoppingWebService/V3/itemSearch";

    private final WebClient webClient;

    @Value("${yahoo.shopping.app-id}")
    private String appId;

    public YahooShoppingClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public YahooShoppingSearchResponse search(String keyword, int page, int hits) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword is required");
        }
        if (page <= 0) page = 1;
        if (hits <= 0 || hits > 100) hits = 20;

        int start = (page - 1) * hits + 1;

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("appid", appId)
                .queryParam("query", keyword)
                .queryParam("results", hits)
                .queryParam("start", start)
                .queryParam("sort", "-score")
                .queryParam("image_size", 300)
                .build()
                .toUriString();

        log.info("[YahooShoppingClient] GET {}", url.replace(appId, "***"));

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(YahooShoppingSearchResponse.class)
                .doOnError(e -> log.error("[YahooShoppingClient] request failed", e))
                .block();
    }
}
