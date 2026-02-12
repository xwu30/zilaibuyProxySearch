package com.zilai.zilaibuy.rakuten.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RakutenItem {
    private String itemName;
    private String catchcopy;
    private String itemCode;
    private Integer itemPrice;
    private String itemCaption;
    private String itemUrl;
    private String affiliateUrl;
    private String shopName;
    private String shopCode;
    private String genreId;

    @JsonProperty("mediumImageUrls")
    private List<ImageUrl> mediumImageUrls;
    @JsonProperty("smallImageUrls")
    private List<ImageUrl> smallImageUrls;
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageUrl {
        private String imageUrl;
    }
}
