package com.zilai.zilaibuy.dto.order;

import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
        Long id,
        String orderNo,
        String packingNo,
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
        String shippingRoute,
        Integer serviceFeeJpy,
        String serviceFeeMemo,
        Integer pointsUsed,
        String quotedRoute,
        Integer quotedFeeJpy,
        String receiverAddress,
        String requestedShippingLine,
        String requestedShippingLineName,
        Integer estimatedShippingFeeJpy,
        String paymentMethod
) {
    public static OrderDto from(OrderEntity e) {
        List<OrderItemDto> items = e.getItems() != null
                ? e.getItems().stream().map(OrderItemDto::from).toList()
                : List.of();
        List<ParcelDto> parcels = e.getLinkedParcels() != null
                ? e.getLinkedParcels().stream().map(ParcelDto::from).toList()
                : List.of();
        String paymentMethod = null;
        if (e.getOttPayOrderRef() != null && !e.getOttPayOrderRef().isBlank()) paymentMethod = "wechat";
        else if (e.getPaypalOrderId() != null && !e.getPaypalOrderId().isBlank()) paymentMethod = "paypal";
        else if (e.getStripePaymentIntentId() != null && !e.getStripePaymentIntentId().isBlank()) paymentMethod = "stripe";
        else if (e.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT) paymentMethod = "balance";
        return new OrderDto(e.getId(), e.getOrderNo(), e.getPackingNo(), e.getStatus().name(),
                e.getTotalCny(), e.getNotes(), e.getTransitTrackingNo(), e.getTransitCarrier(),
                e.getUser().getId(), items, parcels, e.getCreatedAt(), e.getUpdatedAt(),
                e.getWeightG(), e.getLengthCm(), e.getWidthCm(), e.getHeightCm(),
                e.getPackingPhotoUrl(), e.getShippingFeeCny(), e.getShippingRoute(),
                e.getServiceFeeJpy(), e.getServiceFeeMemo(), e.getPointsUsed(),
                e.getQuotedRoute(), e.getQuotedFeeJpy(),
                e.getReceiverAddress(), e.getRequestedShippingLine(),
                e.getRequestedShippingLineName(), e.getEstimatedShippingFeeJpy(),
                paymentMethod);
    }
}
