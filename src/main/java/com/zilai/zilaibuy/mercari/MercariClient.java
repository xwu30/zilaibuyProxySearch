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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calls mercari-listings-scraper actor via Apify:
 * POST https://api.apify.com/v2/acts/0CUfsatHtZBfUO99R/run-sync-get-dataset-items?token=xxx
 *
 * Input:  { "keyword": "...", "limit": N }
 * Output: items with price (string), thumbnails[], status "ITEM_STATUS_ON_SALE", brandName (string)
 */
@Slf4j
@Component
public class MercariClient {

    private static final String APIFY_ENDPOINT =
            "https://api.apify.com/v2/acts/0CUfsatHtZBfUO99R/run-sync-get-dataset-items";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${apify.token:}")
    private String apifyToken;

    // Item shape returned by mercari-listings-scraper actor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApifyItem(
            String id,
            String name,
            String price,           // plain string e.g. "45000"
            List<String> thumbnails,
            String status,
            String url,
            String itemConditionId, // numeric string e.g. "1"
            String brandName        // plain string e.g. "Apple"
    ) {
        public String resolvedImageUrl() {
            if (thumbnails != null && !thumbnails.isEmpty()) return thumbnails.get(0);
            return "";
        }
        public boolean isOnSale() {
            return status == null || status.contains("ON_SALE");
        }
        public Integer resolvedPrice() {
            if (price == null) return 0;
            try { return Integer.parseInt(price.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
        }
        public String resolvedDescription() {
            StringBuilder sb = new StringBuilder();
            if (itemConditionId != null && !itemConditionId.isEmpty()) {
                sb.append("商品成色：").append(conditionLabel(itemConditionId));
            }
            if (brandName != null && !brandName.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("品牌：").append(brandName);
            }
            return sb.toString();
        }
        private static String conditionLabel(String c) {
            return switch (c) {
                case "1" -> "全新";
                case "2" -> "几乎全新";
                case "3" -> "良好";
                case "4" -> "有轻微划痕";
                case "5" -> "有划痕";
                case "6" -> "较差";
                case "7" -> "垃圾品";
                default -> c;
            };
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
                    + "&timeout=120"
                    + "&memory=1024";

            Map<String, Object> input = Map.of(
                    "keyword", keyword,
                    "limit", finalLimit
            );
            byte[] inputBytes = objectMapper.writeValueAsBytes(input);

            log.info("[MercariClient] Apify POST keyword='{}' limit={}", keyword, finalLimit);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(150_000);
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

            List<ApifyItem> items = objectMapper.readValue(
                    body,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApifyItem.class)
            );

            log.info("[MercariClient] Apify returned {} items for '{}'", items.size(), keyword);

            List<MercariSearchResponse.Item> converted = items.stream()
                    .filter(ApifyItem::isOnSale)
                    .map(i -> {
                        String imgUrl = i.resolvedImageUrl();
                        List<String> thumbs = imgUrl.isEmpty() ? List.of() : List.of(imgUrl);
                        return new MercariSearchResponse.Item(
                                i.id(), i.name() != null ? i.name() : "", i.resolvedPrice(),
                                thumbs, "ITEM_STATUS_ON_SALE", null, null, null, null, null,
                                i.resolvedDescription());
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
