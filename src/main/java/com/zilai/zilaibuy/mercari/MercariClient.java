package com.zilai.zilaibuy.mercari;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilai.zilaibuy.mercari.dto.MercariSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calls the Mercari scraper via Apify:
 * POST https://api.apify.com/v2/acts/sovereigntaylor~mercari-scraper/run-sync-get-dataset-items?token=xxx
 *
 * Apify handles geo-blocking and anti-bot — no need for JP IP.
 */
@Slf4j
@Component
public class MercariClient {

    private static final String APIFY_ENDPOINT =
            "https://api.apify.com/v2/acts/sovereigntaylor~mercari-scraper/run-sync-get-dataset-items";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${apify.token:}")
    private String apifyToken;

    // Raw item shape returned by the Apify actor (fields may vary by actor version)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApifyItem(
            String id,
            String itemId,
            String name,
            String title,
            Integer price,
            String imageUrl,
            String thumbnailUrl,
            String photo,
            String url,
            String itemUrl,
            String itemStatus,
            String status
    ) {
        public String resolvedId()       { return id != null ? id : itemId; }
        public String resolvedName()     { return name != null ? name : (title != null ? title : ""); }
        public String resolvedImageUrl() {
            if (imageUrl != null)     return imageUrl;
            if (thumbnailUrl != null) return thumbnailUrl;
            if (photo != null)        return photo;
            return "";
        }
        public String resolvedUrl()      { return url != null ? url : (itemUrl != null ? itemUrl : ""); }
        public boolean isOnSale() {
            String s = itemStatus != null ? itemStatus : (status != null ? status : "");
            return s.isEmpty() || s.contains("on_sale") || s.contains("ON_SALE") || s.contains("ITEM_STATUS_ON_SALE");
        }
    }

    public MercariSearchResponse search(String keyword, int limit, String pageToken) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword is required");
        }
        final int finalLimit = (limit <= 0 || limit > 120) ? 30 : limit;

        if (apifyToken == null || apifyToken.isBlank()) {
            log.warn("[MercariClient] APIFY_TOKEN not configured, returning empty results");
            return emptyResponse();
        }

        try {
            String endpoint = APIFY_ENDPOINT + "?token=" + apifyToken
                    + "&timeout=120"      // wait up to 120s for scraper
                    + "&memory=1024";     // MB RAM for the actor

            // Build input JSON — try "keyword" field first (most common convention)
            Map<String, Object> input = Map.of(
                    "keyword", keyword,
                    "maxItems", finalLimit
            );
            byte[] inputBytes = objectMapper.writeValueAsBytes(input);

            log.info("[MercariClient] Apify POST keyword='{}' limit={}", keyword, finalLimit);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(150_000); // scraping can take a while
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(inputBytes);
            }

            int status = conn.getResponseCode();
            log.info("[MercariClient] Apify HTTP {}", status);

            if (status != 200 && status != 201) {
                InputStream err = conn.getErrorStream();
                String errBody = err != null ? new String(err.readAllBytes()) : "(no body)";
                log.error("[MercariClient] Apify error {} - {}", status,
                        errBody.substring(0, Math.min(500, errBody.length())));
                return emptyResponse();
            }

            String encoding = conn.getContentEncoding();
            InputStream is = conn.getInputStream();
            if ("gzip".equalsIgnoreCase(encoding)) {
                is = new java.util.zip.GZIPInputStream(is);
            }
            byte[] body = is.readAllBytes();
            is.close();

            // Apify run-sync-get-dataset-items returns a JSON array directly
            List<ApifyItem> items = objectMapper.readValue(
                    body,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApifyItem.class)
            );

            log.info("[MercariClient] Apify returned {} items for '{}'", items.size(), keyword);

            // Convert to our standard MercariSearchResponse
            List<MercariSearchResponse.Item> converted = items.stream()
                    .filter(ApifyItem::isOnSale)
                    .map(i -> {
                        List<String> thumbs = (i.resolvedImageUrl() != null && !i.resolvedImageUrl().isEmpty())
                                ? List.of(i.resolvedImageUrl()) : List.of();
                        String itemSt = i.itemStatus() != null ? i.itemStatus() : "ITEM_STATUS_ON_SALE";
                        return new MercariSearchResponse.Item(
                                i.resolvedId(), i.resolvedName(), i.price(),
                                thumbs, itemSt, null, null, null, null, null);
                    })
                    .toList();

            return new MercariSearchResponse(
                    converted,
                    new MercariSearchResponse.Meta(converted.size(), null, false)
            );

        } catch (Exception e) {
            log.error("[MercariClient] Apify request error: {}", e.getMessage(), e);
            return emptyResponse();
        }
    }

    private MercariSearchResponse emptyResponse() {
        return new MercariSearchResponse(
                Collections.emptyList(),
                new MercariSearchResponse.Meta(0, null, false)
        );
    }
}
