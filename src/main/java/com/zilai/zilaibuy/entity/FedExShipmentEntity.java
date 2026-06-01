package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fedex_shipments", indexes = {
        @Index(name = "idx_fedex_created_at", columnList = "created_at"),
        @Index(name = "idx_fedex_tracking", columnList = "tracking_no")
})
@Getter
@Setter
public class FedExShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_no", length = 50)
    private String trackingNo;

    @Column(name = "recipient_name", length = 200, nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @Column(name = "recipient_address", length = 300, nullable = false)
    private String recipientAddress;

    @Column(name = "recipient_city", length = 100)
    private String recipientCity;

    @Column(name = "recipient_state", length = 50)
    private String recipientState;

    @Column(name = "recipient_postal", length = 20)
    private String recipientPostal;

    @Column(name = "recipient_country", length = 5)
    private String recipientCountry;

    @Column(name = "weight_lbs")
    private Double weightLbs;

    @Column(name = "length_in")
    private Integer lengthIn;

    @Column(name = "width_in")
    private Integer widthIn;

    @Column(name = "height_in")
    private Integer heightIn;

    @Column(name = "service_type", length = 60)
    private String serviceType;

    @Column(name = "net_charge", precision = 10, scale = 2)
    private BigDecimal netCharge;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "CREATED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
