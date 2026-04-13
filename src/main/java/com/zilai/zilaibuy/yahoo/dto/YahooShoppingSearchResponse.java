package com.zilai.zilaibuy.yahoo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooShoppingSearchResponse(
        @JsonProperty("totalResultsReturned")  int totalResultsReturned,
        @JsonProperty("totalResultsAvailable") int totalResultsAvailable,
        @JsonProperty("firstResultPosition")   int firstResultPosition,
        @JsonProperty("hits")                  List<Hit> hits
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(
            int index,
            String name,
            String description,
            String url,
            PriceLabel priceLabel,
            Image image,
            Review review,
            Seller seller
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceLabel(
            @JsonProperty("taxIncluded")   Boolean taxIncluded,
            @JsonProperty("defaultPrice")  Integer defaultPrice,
            @JsonProperty("sellingPrice")  Integer sellingPrice
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(
            String small,
            String medium
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(
            int count,
            double rate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Seller(
            String sellerId,
            String name
    ) {}
}
