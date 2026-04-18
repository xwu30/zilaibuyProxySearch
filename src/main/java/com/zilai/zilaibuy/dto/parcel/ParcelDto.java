package com.zilai.zilaibuy.dto.parcel;

import com.zilai.zilaibuy.entity.ForwardingParcelEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParcelDto(
        Long id,
        Long userId,
        String userPhone,
        String username,
        String inboundTrackingNo,
        String carrier,
        String content,
        BigDecimal declaredValue,
        String status,
        String processingOption,
        Double weight,
        String outboundTrackingNo,
        String notes,
        Long linkedOrderId,
        String linkedOrderNo,
        String warehouseLocation,
        String inboundCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String hbrMessage
) {
    public static ParcelDto from(ForwardingParcelEntity e) {
        return from(e, null);
    }

    public static ParcelDto from(ForwardingParcelEntity e, String hbrMessage) {
        return new ParcelDto(
                e.getId(),
                e.getUser().getId(),
                e.getUser().getPhone(),
                e.getUser().getUsername(),
                e.getInboundTrackingNo(),
                e.getCarrier(),
                e.getContent(),
                e.getDeclaredValue(),
                e.getStatus().name(),
                e.getProcessingOption(),
                e.getWeight(),
                e.getOutboundTrackingNo(),
                e.getNotes(),
                e.getLinkedOrder() != null ? e.getLinkedOrder().getId() : null,
                e.getLinkedOrder() != null ? e.getLinkedOrder().getOrderNo() : null,
                e.getWarehouseLocation(),
                e.getInboundCode(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                hbrMessage
        );
    }
}
