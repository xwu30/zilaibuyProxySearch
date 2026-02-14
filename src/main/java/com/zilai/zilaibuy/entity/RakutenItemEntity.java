package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rakuten_item")
@Getter
@Setter
public class RakutenItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="keyword", nullable=false, length=200)
    private String keyword;

    @Column(name="synced_at", nullable=false)
    private LocalDateTime syncedAt;

    @Column(name = "item_code", nullable = false, unique = true)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 512)
    private String itemName;

    @Column(name = "catch_copy", length = 512)
    private String catchCopy;

    @Column(name = "item_name_zh", length = 512)
    private String itemNameZh;

    @Column(name = "catch_copy_zh", length = 512)
    private String catchCopyZh;

    @Column(name = "item_price", nullable = false)
    private Integer itemPrice;

    @Column(name = "affiliate_url", length = 1024)
    private String affiliateUrl;

    @Column(name = "item_url", length = 1024)
    private String itemUrl;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Column(name = "shop_code", length = 128)
    private String shopCode;

    @Column(name = "genre_id", length = 32)
    private String genreId;

    @Column(name = "image_small", length = 1024)
    private String imageSmall;

    @Column(name = "image_medium", length = 1024)
    private String imageMedium;

    @Column(name = "review_average", precision = 3, scale = 2)
    private BigDecimal reviewAverage;

    @Column(name = "review_count")
    private Integer reviewCount;

    private Integer availability;
    private Integer postageFlag;
    private Integer creditCardFlag;

    @Column(name = "last_fetched_at", nullable = false)
    private LocalDateTime lastFetchedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
