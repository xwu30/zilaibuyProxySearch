package com.zilai.zilaibuy.entity;

import com.zilai.zilaibuy.entity.OrderEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "forwarding_parcels", indexes = {
        @Index(name = "idx_fwd_parcels_user", columnList = "user_id"),
        @Index(name = "idx_fwd_parcels_status", columnList = "status"),
        @Index(name = "idx_fwd_parcels_tracking", columnList = "inbound_tracking_no")
})
@Getter
@Setter
public class ForwardingParcelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "inbound_tracking_no", length = 100)
    private String inboundTrackingNo;

    @Column(length = 100)
    private String carrier;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "declared_value", precision = 10, scale = 2)
    private BigDecimal declaredValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelStatus status = ParcelStatus.ANNOUNCED;

    @Column(name = "processing_option", length = 20)
    private String processingOption = "direct";

    @Column
    private Integer weight;

    @Column(name = "outbound_tracking_no", length = 100)
    private String outboundTrackingNo;

    /** When non-null, this parcel is packed together with the linked proxy order */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_order_id")
    private OrderEntity linkedOrder;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ParcelStatus {
        ANNOUNCED, IN_WAREHOUSE, PACKING, SHIPPED, DELIVERED
    }
}
