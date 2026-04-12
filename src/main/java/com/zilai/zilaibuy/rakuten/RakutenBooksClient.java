package com.zilai.zilaibuy.rakuten;

import com.zilai.zilaibuy.rakuten.dto.RakutenBooksSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class RakutenBooksClient {

    private static final String BOOKS_API_URL = "https://app.rakuten.co.jp/services/api/BooksBook/Search/20170404";

    private final WebClient webClient;
    private final RakutenProperties props;

    // 无 baseUrl 的独立 WebClient，使用完整绝对 URL 避免编码问题
    public RakutenBooksClient(WebClient.Builder webClientBuilder, RakutenProperties props) {
        this.webClient = webClientBuilder.build();
        this.props = props;
    }

    public RakutenBooksSearchResponse search(String keyword, int page, int hits) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword is required");
        }
        if (page <= 0) page = 1;
        if (hits <= 0 || hits > 30) hits = 20;

        // 用 encode() 显式 UTF-8 编码，正确处理中文/日文关键词
        java.net.URI uri = UriComponentsBuilder
                .fromHttpUrl(BOOKS_API_URL)
                .queryParam("format", "json")
                .queryParam("keyword", keyword)
                .queryParam("page", page)
                .queryParam("hits", hits)
                .queryParam("applicationId", props.getApplicationId())
                .encode()
                .build()
                .toUri();

        log.info("[RakutenBooksClient] GET keyword={} page={} hits={}", keyword, page, hits);

        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.REFERER, props.getReferer())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(RakutenBooksSearchResponse.class)
                .doOnError(e -> log.error("[RakutenBooksClient] request failed", e))
                .block();
    }
}
