package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public record OrderDetailDto(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalCny,
        String notes,
        Long userId,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String transitTrackingNo,
        String transitCarrier,
        List<ParcelDto> linkedParcels,
        Integer weightG,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        String packingPhotoUrl,
        BigDecimal shippingFeeCny,
        String shippingRoute,
        Integer serviceFeeJpy,
        String serviceFeeMemo,
        String quotedRoute,
        Integer quotedFeeJpy
) {
    public static OrderDetailDto from(OrderEntity e) {
        return from(e, Collections.emptyList());
    }

    public static OrderDetailDto from(OrderEntity e, List<ForwardingParcelEntity> parcels) {
        List<OrderItemDto> items = e.getItems().stream().map(OrderItemDto::from).toList();
        List<ParcelDto> linkedParcels = parcels.stream().map(ParcelDto::from).toList();
        return new OrderDetailDto(e.getId(), e.getOrderNo(), e.getStatus().name(),
                e.getTotalCny(), e.getNotes(), e.getUser().getId(),
                items, e.getCreatedAt(), e.getUpdatedAt(),
                e.getTransitTrackingNo(), e.getTransitCarrier(), linkedParcels,
                e.getWeightG(), e.getLengthCm(), e.getWidthCm(), e.getHeightCm(),
                e.getPackingPhotoUrl(), e.getShippingFeeCny(), e.getShippingRoute(),
                e.getServiceFeeJpy(), e.getServiceFeeMemo(),
                e.getQuotedRoute(), e.getQuotedFeeJpy());
    }
}
