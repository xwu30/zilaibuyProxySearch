package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vas_requests", indexes = {
        @Index(name = "idx_vas_user", columnList = "user_id"),
        @Index(name = "idx_vas_status", columnList = "status")
})
@Getter
@Setter
public class VasRequestEntity {

    public enum VasStatus { PENDING, PROCESSING, DONE, PAID, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** Comma-separated parcel IDs */
    @Column(name = "parcel_ids", length = 500)
    private String parcelIds;

    /** Comma-separated order IDs */
    @Column(name = "order_ids", length = 500)
    private String orderIds;

    /** Comma-separated service codes, e.g. "item_inspect,photo" */
    @Column(name = "services", length = 300, nullable = false)
    private String services;

    /** Human-readable summary of items (tracking nos, order nos) */
    @Column(name = "items_summary", length = 1000)
    private String itemsSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VasStatus status = VasStatus.PENDING;

    /** Admin notes / processing remarks */
    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    /**
     * JSON array of per-service results.
     * Format: [{"serviceId":"item_inspect","notes":"...","photoUrls":["url1","url2"]}, ...]
     */
    @Column(name = "service_results", length = 4000)
    private String serviceResults;

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;

    @Column(name = "paypal_order_id", length = 100)
    private String paypalOrderId;

    @Column(name = "ottpay_order_ref", length = 100)
    private String ottPayOrderRef;

    /** For custom VAS tasks: user's free-form description */
    @Column(name = "custom_description", columnDefinition = "TEXT")
    private String customDescription;

    /** For custom VAS tasks: user's budget in JPY */
    @Column(name = "custom_budget_jpy")
    private Integer customBudgetJpy;

    /** For custom VAS tasks: JSON array of user-uploaded image URLs */
    @Column(name = "custom_image_urls", columnDefinition = "TEXT")
    private String customImageUrls;

    /** Admin's quoted price in JPY for custom tasks */
    @Column(name = "admin_quote_jpy")
    private Integer adminQuoteJpy;

    /**
     * Custom tasks only: the customer's latest counter-offer when haggling over the
     * warehouse quote. Set when the customer rejects a DONE quote and bounces the task
     * back to PROCESSING for re-quoting. Cleared/overwritten on each new counter-offer.
     */
    @Column(name = "customer_counter_note", columnDefinition = "TEXT")
    private String customerCounterNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
