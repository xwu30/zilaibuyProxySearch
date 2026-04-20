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

    /** 增值服务，逗号分隔，如 "photo_label,item_check" */
    @Column(name = "service_options", length = 200)
    private String serviceOptions;

    @Column
    private Double weight;  // kg

    @Column(name = "outbound_tracking_no", length = 100)
    private String outboundTrackingNo;

    /** 库位号，如 A01 */
    @Column(name = "warehouse_location", length = 20)
    private String warehouseLocation;

    /** 入库编号，格式 ZL-YYMM-{userId:5位}-{location}-{seq:3位}，唯一 */
    @Column(name = "inbound_code", length = 50, unique = true)
    private String inboundCode;

    /** When non-null, this parcel is packed together with the linked proxy order */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_order_id")
    private OrderEntity linkedOrder;

    @Column(length = 500)
    private String notes;

    /** 实际入库时间，由仓库扫码入库时设置 */
    @Column(name = "checkin_date")
    private LocalDateTime checkinDate;

    /** 是否已发送60天仓储费提醒邮件 */
    @Column(name = "storage_fee_reminder_sent", nullable = false)
    private boolean storageFeeReminderSent = false;

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
