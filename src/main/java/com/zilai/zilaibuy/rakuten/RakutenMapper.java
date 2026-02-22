package com.zilai.zilaibuy.rakuten;

import com.zilai.zilaibuy.dto.ZilaiItemDto;
import com.zilai.zilaibuy.entity.RakutenItemEntity;
import com.zilai.zilaibuy.rakuten.dto.RakutenIchibaSearchResponse;
import com.zilai.zilaibuy.rakuten.dto.RakutenItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RakutenMapper {

    public ZilaiItemDto toZilaiItem(RakutenItem item) {
        String image = firstImage(item.getMediumImageUrls());
        if (image == null) image = firstImage(item.getSmallImageUrls());

        String finalUrl = (item.getAffiliateUrl() != null && !item.getAffiliateUrl().isBlank())
                ? item.getAffiliateUrl()
                : item.getItemUrl();

        return new ZilaiItemDto(
                item.getItemName(),
                item.getItemCaption(),
                finalUrl,
                item.getShopName(),
                item.getItemPrice(),
                image
        );
    }

    public RakutenItemEntity toEntity(RakutenIchibaSearchResponse.Item item, String keyword,
                                      String itemNameZh, String catchCopyZh) {
        RakutenItemEntity entity = new RakutenItemEntity();
        entity.setKeyword(keyword);
        entity.setSyncedAt(LocalDateTime.now());
        entity.setItemCode(item.itemCode());
        entity.setItemName(item.itemName());
        entity.setCatchCopy(item.catchcopy());
        entity.setItemNameZh(itemNameZh);
        entity.setCatchCopyZh(catchCopyZh);
        entity.setItemPrice(item.itemPrice());
        entity.setAffiliateUrl(item.affiliateUrl());
        entity.setItemUrl(item.itemUrl());
        entity.setShopName(item.shopName());
        entity.setShopCode(item.shopCode());
        entity.setGenreId(item.genreId());
        entity.setImageSmall(firstRecordImage(item.smallImageUrls()));
        entity.setImageMedium(firstRecordImage(item.mediumImageUrls()));
        entity.setReviewAverage(item.reviewAverage() != null
                ? BigDecimal.valueOf(item.reviewAverage()) : null);
        entity.setReviewCount(item.reviewCount());
        entity.setAvailability(item.availability());
        entity.setPostageFlag(item.postageFlag());
        entity.setCreditCardFlag(item.creditCardFlag());
        entity.setLastFetchedAt(LocalDateTime.now());
        entity.setIsActive(true);
        return entity;
    }

    public void updateEntity(RakutenItemEntity entity, RakutenIchibaSearchResponse.Item item,
                             String itemNameZh, String catchCopyZh) {
        entity.setItemName(item.itemName());
        entity.setCatchCopy(item.catchcopy());
        entity.setItemNameZh(itemNameZh);
        entity.setCatchCopyZh(catchCopyZh);
        entity.setItemPrice(item.itemPrice());
        entity.setAffiliateUrl(item.affiliateUrl());
        entity.setItemUrl(item.itemUrl());
        entity.setShopName(item.shopName());
        entity.setShopCode(item.shopCode());
        entity.setGenreId(item.genreId());
        entity.setImageSmall(firstRecordImage(item.smallImageUrls()));
        entity.setImageMedium(firstRecordImage(item.mediumImageUrls()));
        entity.setReviewAverage(item.reviewAverage() != null
                ? BigDecimal.valueOf(item.reviewAverage()) : null);
        entity.setReviewCount(item.reviewCount());
        entity.setAvailability(item.availability());
        entity.setPostageFlag(item.postageFlag());
        entity.setCreditCardFlag(item.creditCardFlag());
        entity.setLastFetchedAt(LocalDateTime.now());
        entity.setIsActive(true);
    }

    private String firstImage(List<RakutenItem.ImageUrl> images) {
        if (images == null || images.isEmpty()) return null;
        return images.get(0).getImageUrl();
    }

    private String firstRecordImage(List<RakutenIchibaSearchResponse.ImageUrl> images) {
        if (images == null || images.isEmpty()) return null;
        return images.get(0).imageUrl();
    }
}
