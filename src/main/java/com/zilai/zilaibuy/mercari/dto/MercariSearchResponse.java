package com.zilai.zilaibuy.mercari.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercariSearchResponse(
        List<Item> items,
        Meta meta
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            String name,
            Integer price,
            List<String> thumbnails,
            String itemStatus,
            String sellerId,
            Integer itemConditionId,
            Integer shippingPayerId,
            Long updated,
            Long created
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            Integer numFound,
            String nextPageToken,
            Boolean hasMore
    ) {}
}
