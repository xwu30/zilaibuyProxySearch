package com.zilai.zilaibuy.dto.parcel;

import com.zilai.zilaibuy.entity.ForwardingParcelEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParcelDto(
        Long id,
        Long userId,
        String inboundTrackingNo,
        String carrier,
        String content,
        BigDecimal declaredValue,
        String status,
        String processingOption,
        Integer weight,
        String outboundTrackingNo,
        String notes,
        Long linkedOrderId,
        String warehouseLocation,
        String inboundCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ParcelDto from(ForwardingParcelEntity e) {
        return new ParcelDto(
                e.getId(),
                e.getUser().getId(),
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
                e.getWarehouseLocation(),
                e.getInboundCode(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
