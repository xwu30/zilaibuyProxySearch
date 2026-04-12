package com.zilai.zilaibuy.rakuten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RakutenBooksSearchResponse(
        int count,
        int page,
        int first,
        int last,
        int hits,
        int pageCount,

        @JsonProperty("Items")
        List<ItemWrapper> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemWrapper(
            @JsonProperty("Item")
            Item item
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String titleKana,
            String author,
            String authorKana,
            String isbn,
            Integer itemPrice,
            String itemUrl,
            String largeImageUrl,
            String mediumImageUrl,
            String smallImageUrl,
            String publisherName,
            String salesDate,
            String itemCaption,
            Integer reviewCount,
            String reviewAverage,
            String booksGenreId
    ) {}
}
