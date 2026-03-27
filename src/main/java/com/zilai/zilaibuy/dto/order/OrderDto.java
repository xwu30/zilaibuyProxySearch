package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalCny,
        String notes,
        String transitTrackingNo,
        String transitCarrier,
        Long userId,
        List<OrderItemDto> items,
        List<ParcelDto> linkedParcels,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer weightG,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        String packingPhotoUrl,
        BigDecimal shippingFeeCny,
        String shippingRoute
) {
    public static OrderDto from(OrderEntity e) {
        List<OrderItemDto> items = e.getItems() != null
                ? e.getItems().stream().map(OrderItemDto::from).toList()
                : List.of();
        List<ParcelDto> parcels = e.getLinkedParcels() != null
                ? e.getLinkedParcels().stream().map(ParcelDto::from).toList()
                : List.of();
        return new OrderDto(e.getId(), e.getOrderNo(), e.getStatus().name(),
                e.getTotalCny(), e.getNotes(), e.getTransitTrackingNo(), e.getTransitCarrier(),
                e.getUser().getId(), items, parcels, e.getCreatedAt(), e.getUpdatedAt(),
                e.getWeightG(), e.getLengthCm(), e.getWidthCm(), e.getHeightCm(),
                e.getPackingPhotoUrl(), e.getShippingFeeCny(), e.getShippingRoute());
    }
}
