package com.zilai.zilaibuy.rakuten;

import com.zilai.zilaibuy.rakuten.dto.RakutenBooksSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class RakutenBooksClient {

    private static final String BOOKS_API_URL = "https://app.rakuten.co.jp/services/api/BooksBook/Search/20170404";

    private final WebClient webClient;
    private final RakutenProperties props;

    public RakutenBooksSearchResponse search(String keyword, int page, int hits) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword is required");
        }
        if (page <= 0) page = 1;
        if (hits <= 0 || hits > 30) hits = 20;

        String url = UriComponentsBuilder
                .fromHttpUrl(BOOKS_API_URL)
                .queryParam("format", "json")
                .queryParam("keyword", keyword)
                .queryParam("page", page)
                .queryParam("hits", hits)
                .queryParam("applicationId", props.getApplicationId())
                .build()
                .toUriString();

        log.info("[RakutenBooksClient] GET keyword={} page={} hits={}", keyword, page, hits);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.REFERER, props.getReferer())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(RakutenBooksSearchResponse.class)
                .doOnError(e -> log.error("[RakutenBooksClient] request failed", e))
                .block();
    }
}
