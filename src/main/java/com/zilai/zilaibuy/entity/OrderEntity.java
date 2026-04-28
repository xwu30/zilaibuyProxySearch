package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status")
})
@Getter
@Setter
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "order_no", unique = true, nullable = false, length = 32)
    private String orderNo;

    @Column(name = "packing_no", unique = true, length = 32)
    private String packingNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "total_cny", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCny;

    @Column(length = 500)
    private String notes;

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "linkedOrder", fetch = FetchType.LAZY)
    private List<ForwardingParcelEntity> linkedParcels = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "transit_tracking_no", length = 100)
    private String transitTrackingNo;

    @Column(name = "transit_carrier", length = 50)
    private String transitCarrier;

    @Column(name = "points_used", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int pointsUsed = 0;

    @Column(name = "weight_g")
    private Integer weightG;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "packing_photo_url", length = 500)
    private String packingPhotoUrl;

    @Column(name = "shipping_fee_cny", precision = 10, scale = 2)
    private BigDecimal shippingFeeCny;

    @Column(name = "shipping_route", length = 50)
    private String shippingRoute;

    @Column(name = "quoted_route", length = 100)
    private String quotedRoute;

    @Column(name = "quoted_fee_jpy")
    private Integer quotedFeeJpy;

    @Column(name = "service_fee_jpy")
    private Integer serviceFeeJpy;

    @Column(name = "service_fee_memo", length = 500)
    private String serviceFeeMemo;

    /** JSON string of the receiver address submitted with the shipping request */
    @Column(name = "receiver_address", columnDefinition = "TEXT")
    private String receiverAddress;

    /** Shipping line code selected by customer at shipping request time */
    @Column(name = "requested_shipping_line", length = 100)
    private String requestedShippingLine;

    /** Human-readable shipping line name (e.g. "加拿大包税专线") */
    @Column(name = "requested_shipping_line_name", length = 200)
    private String requestedShippingLineName;

    /** Estimated shipping fee in JPY from HBR quote at time of request */
    @Column(name = "estimated_shipping_fee_jpy")
    private Integer estimatedShippingFeeJpy;

    public enum OrderStatus {
        PENDING_PAYMENT, FEE_QUOTED, PURCHASING, IN_TRANSIT, IN_WAREHOUSE, PACKING, AWAITING_PAYMENT, SHIPPED, DELIVERED, CANCELLED
    }
}
