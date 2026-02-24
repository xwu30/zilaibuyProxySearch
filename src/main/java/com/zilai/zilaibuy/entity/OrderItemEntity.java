package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_title", nullable = false, length = 512)
    private String productTitle;

    @Column(name = "original_url", length = 1024)
    private String originalUrl;

    @Column(name = "price_jpy", nullable = false)
    private int priceJpy;

    @Column(name = "price_cny", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceCny;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(length = 255)
    private String remarks;

    @Column(name = "domestic_shipping", precision = 10, scale = 2)
    private BigDecimal domesticShipping = BigDecimal.ZERO;

    @Column(name = "exchange_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal exchangeRate;

    @Column(length = 50)
    private String platform;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;
}
