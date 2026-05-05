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
 * Calls jupri/mercari-jp actor via Apify:
 * POST https://api.apify.com/v2/acts/mdtMXcvkUXqYZCeQ8/run-sync-get-dataset-items?token=xxx
 *
 * This actor scrapes jp.mercari.com directly with residential proxies.
 * Input: { "query": ["keyword"], "limit": N, "sort": "score" }
 * Output: items with price.value (string), thumbnails[], status "ON_SALE", url (jp.mercari.com)
 */
@Slf4j
@Component
public class MercariClient {

    private static final String APIFY_ENDPOINT =
            "https://api.apify.com/v2/acts/mdtMXcvkUXqYZCeQ8/run-sync-get-dataset-items";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${apify.token:}")
    private String apifyToken;

    // Price object returned by jupri/mercari-jp
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApifyPrice(String currency, String value) {
        public Integer intValue() {
            if (value == null) return 0;
            try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApifyBrand(String id, String name) {}

    // Item shape returned by jupri/mercari-jp actor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApifyItem(
            String id,
            String name,
            ApifyPrice price,
            List<String> thumbnails,
            String status,
            String url,
            String itemCondition,
            ApifyBrand itemBrand
    ) {
        public String resolvedImageUrl() {
            if (thumbnails != null && !thumbnails.isEmpty()) return thumbnails.get(0);
            return "";
        }
        public boolean isOnSale() {
            return status == null || status.equals("ON_SALE");
        }
        public Integer resolvedPrice() {
            return price != null ? price.intValue() : 0;
        }
        public String resolvedDescription() {
            StringBuilder sb = new StringBuilder();
            if (itemCondition != null && !itemCondition.isEmpty()) {
                sb.append("商品成色：").append(conditionLabel(itemCondition));
            }
            if (itemBrand != null && itemBrand.name() != null && !itemBrand.name().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("品牌：").append(itemBrand.name());
            }
            return sb.toString();
        }
        private static String conditionLabel(String c) {
            return switch (c) {
                case "NEW" -> "全新";
                case "LIKE_NEW", "NO_SCRATCH" -> "几乎全新";
                case "GOOD" -> "良好";
                case "LITTLE_SCRATCH" -> "有轻微划痕";
                case "SCRATCH" -> "有划痕";
                case "BAD" -> "较差";
                case "JUNK" -> "垃圾品";
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
                    "query", List.of(keyword),
                    "limit", finalLimit,
                    "sort", "score"
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
