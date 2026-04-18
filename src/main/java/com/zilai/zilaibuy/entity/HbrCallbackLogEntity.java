package com.zilai.zilaibuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hbr_callback_logs", indexes = {
        @Index(name = "idx_hbr_log_created", columnList = "created_at"),
        @Index(name = "idx_hbr_log_type",    columnList = "callback_type")
})
@Getter
@Setter
public class HbrCallbackLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "inbound" or "shipment" */
    @Column(name = "callback_type", nullable = false, length = 20)
    private String callbackType;

    /** 预报回推: inboundTrackingNo；合箱回推: packingNo */
    @Column(name = "tracking_key", length = 100)
    private String trackingKey;

    /** HBR 原始 status 字符串 */
    @Column(name = "raw_status", length = 50)
    private String rawStatus;

    /** 映射后的内部状态，如 IN_WAREHOUSE */
    @Column(name = "internal_status", length = 30)
    private String internalStatus;

    /** 是否实际更新了数据库记录 */
    @Column(name = "status_updated", nullable = false)
    private boolean statusUpdated = false;

    /** 处理结果说明（成功/错误/忽略原因） */
    @Column(length = 500)
    private String message;

    /** 完整请求 body（去掉 appToken/appKey） */
    @Column(name = "raw_body", columnDefinition = "TEXT")
    private String rawBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
