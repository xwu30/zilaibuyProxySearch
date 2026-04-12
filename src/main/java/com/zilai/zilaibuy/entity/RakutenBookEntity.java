package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rakuten_book")
@Getter
@Setter
public class RakutenBookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false, length = 200)
    private String keyword;

    @Column(name = "isbn", nullable = false, unique = true, length = 64)
    private String isbn;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "title_zh", length = 512)
    private String titleZh;

    @Column(name = "author", length = 255)
    private String author;

    @Column(name = "publisher_name", length = 255)
    private String publisherName;

    @Column(name = "sales_date", length = 64)
    private String salesDate;

    @Column(name = "item_price")
    private Integer itemPrice;

    @Column(name = "item_url", length = 1024)
    private String itemUrl;

    @Column(name = "large_image_url", length = 1024)
    private String largeImageUrl;

    @Column(name = "medium_image_url", length = 1024)
    private String mediumImageUrl;

    @Column(name = "small_image_url", length = 1024)
    private String smallImageUrl;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "review_average", precision = 3, scale = 2)
    private BigDecimal reviewAverage;

    @Column(name = "books_genre_id", length = 32)
    private String booksGenreId;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "last_fetched_at", nullable = false)
    private LocalDateTime lastFetchedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
