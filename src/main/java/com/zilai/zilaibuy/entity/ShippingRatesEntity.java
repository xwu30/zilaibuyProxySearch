package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 单行配置表，始终只有 id=1 的一行，存储运费价格 JSON。
 */
@Entity
@Table(name = "shipping_rates")
@Getter
@Setter
public class ShippingRatesEntity {

    @Id
    private Long id = 1L;

    @Column(name = "rates_json", columnDefinition = "TEXT", nullable = false)
    private String ratesJson;

    @Column(name = "updated_by")
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
