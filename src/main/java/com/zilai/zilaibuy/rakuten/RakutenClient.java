package com.zilai.zilaibuy.rakuten;

import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
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
public class RakutenClient {
    private final WebClient webClient;
    private final RakutenProperties props;
    public RakutenIchibaSearchResponse search(String keyword, int hits) {
        return search(keyword, 1, hits);
    }
    // ✅ 最终统一签名：返回 DTO
    public RakutenIchibaSearchResponse search(String keyword, int page, int hits) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword is required");
        }
        if (page <= 0) page = 1;
        if (hits <= 0) hits = 10;

        String url = UriComponentsBuilder
                .fromHttpUrl(getBaseUrl() + getPath())
                .queryParam("format", "json")
                .queryParam("keyword", keyword)
                .queryParam("page", page)
                .queryParam("hits", hits)
                .queryParam("imageFlag", getDefaultImageFlag())
                .queryParam("applicationId", getApplicationId())
                .queryParam("affiliateId", getAffiliateId())
                // 你现在是同时带了 accessKey（你已在请求里这么做），保留：
                .queryParam("accessKey", getAccessKey())
                .build()
                .toUriString();

        log.info("[RakutenClient] GET {}", url);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.REFERER, getReferer()) // ✅ 解决 403 REFERER_MISSING
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(RakutenIchibaSearchResponse.class)
                .doOnError(e -> log.error("[RakutenClient] request failed", e))
                .block();
    }

    // ====== 下面这些 getter 兼容 record / 普通 class 二选一 ======
    private String getBaseUrl() {
        // record: props.baseUrl()
        // class:  props.getBaseUrl()
        return tryRecordOrGetter("baseUrl");
    }

    private String getPath() {
        return tryRecordOrGetter("path");
    }

    private String getApplicationId() {
        return tryRecordOrGetter("applicationId");
    }

    private String getAffiliateId() {
        return tryRecordOrGetter("affiliateId");
    }

    private String getAccessKey() {
        return tryRecordOrGetter("accessKey");
    }

    private String getReferer() {
        // 你可以放配置里，比如 https://zilaibuy.com
        // record: props.referer() / class: props.getReferer()
        return tryRecordOrGetter("referer");
    }

    private int getDefaultImageFlag() {
        // record: props.defaultImageFlag()
        // class:  props.getDefaultImageFlag()
        String v = tryRecordOrGetter("defaultImageFlag");
        return Integer.parseInt(v);
    }

    // ⚠️ 这个方法只是为了让你复制时少改动：你实际项目里请直接写 props.baseUrl() 或 props.getBaseUrl()
    private String tryRecordOrGetter(String field) {
        try {
            // record accessor
            return String.valueOf(props.getClass().getMethod(field).invoke(props));
        } catch (Exception ignore) {
            try {
                // getter
                String m = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
                return String.valueOf(props.getClass().getMethod(m).invoke(props));
            } catch (Exception e) {
                throw new IllegalStateException("RakutenProperties missing field: " + field
                        + " (record accessor or getter not found)", e);
            }
        }
    }
}
