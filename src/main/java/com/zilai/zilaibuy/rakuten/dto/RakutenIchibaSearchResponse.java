package com.zilai.zilaibuy.rakuten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RakutenIchibaSearchResponse(
        int count,
        int page,
        int first,
        int last,
        int hits,
        int carrier,
        int pageCount,

        // 你的真实返回是大写 "Items"
        @JsonProperty("Items")
        List<ItemWrapper> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemWrapper(
            // 每个元素里面是 {"Item": {...}}
            @JsonProperty("Item")
            Item item
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String itemName,
            String catchcopy,
            String itemCode,
            Integer itemPrice,
            String itemCaption,
            String itemUrl,
            String shopUrl,
            String affiliateUrl,
            String shopAffiliateUrl,
            Integer imageFlag,
            Integer availability,
            Integer taxFlag,
            Integer postageFlag,
            Integer creditCardFlag,
            String shopName,
            String shopCode,
            String genreId,
            Integer reviewCount,
            Double reviewAverage,
            Integer pointRate,

            List<ImageUrl> smallImageUrls,
            List<ImageUrl> mediumImageUrls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageUrl(
            String imageUrl
    ) {}
}
