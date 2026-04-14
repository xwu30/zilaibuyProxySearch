package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.order.*;
import com.zilai.zilaibuy.dto.order.UpdateItemTrackingRequest;
import java.math.BigDecimal;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.OrderItemEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import com.zilai.zilaibuy.repository.OrderItemRepository;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ForwardingParcelRepository parcelRepository;
    private final HbrService hbrService;
    private final EmailService emailService;

    @Transactional
    public OrderDto createOrder(CreateOrderRequest req, Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));

        OrderEntity order = new OrderEntity();
        order.setUser(user);
        order.setOrderNo(generateOrderNo());
        order.setTotalCny(req.totalCny());
        order.setNotes(req.notes());

        for (OrderItemRequest itemReq : req.items()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrder(order);
            item.setProductTitle(itemReq.productTitle());
            item.setOriginalUrl(itemReq.originalUrl());
            item.setPriceJpy(itemReq.priceJpy());
            item.setPriceCny(itemReq.priceCny());
            item.setQuantity(itemReq.quantity());
            item.setRemarks(itemReq.remarks());
            item.setDomesticShipping(itemReq.domesticShipping() != null ? itemReq.domesticShipping() : java.math.BigDecimal.ZERO);
            item.setExchangeRate(itemReq.exchangeRate());
            item.setPlatform(itemReq.platform());
            item.setImageUrl(itemReq.imageUrl());
            item.setReferenceImages(serializeReferenceImages(itemReq.referenceImages()));
            order.getItems().add(item);
        }

        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public void saveReferenceImages(Long orderId, java.util.Map<String, Object> body, Long userId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getUser().getId().equals(userId))
            throw new AppException(HttpStatus.FORBIDDEN, "无权操作此订单");

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> imagesList = (List<java.util.Map<String, Object>>) body.get("images");
        if (imagesList == null) return;

        for (java.util.Map<String, Object> entry : imagesList) {
            String originalUrl = (String) entry.get("originalUrl");
            @SuppressWarnings("unchecked")
            List<String> refs = (List<String>) entry.get("referenceImages");
            if (originalUrl == null || refs == null || refs.isEmpty()) continue;

            order.getItems().stream()
                    .filter(i -> originalUrl.equals(i.getOriginalUrl()))
                    .findFirst()
                    .ifPresent(i -> i.setReferenceImages(serializeReferenceImages(refs)));
        }
        orderRepository.save(order);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String serializeReferenceImages(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(images);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> listOrders(AuthenticatedUser currentUser, Long filterUserId,
                                     OrderEntity.OrderStatus status,
                                     java.time.LocalDate dateFrom, java.time.LocalDate dateTo,
                                     String q,
                                     Pageable pageable) {
        boolean isPrivileged = isPrivileged(currentUser.role());
        Long effectiveUserId = isPrivileged ? filterUserId : currentUser.id();
        java.time.LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        java.time.LocalDateTime to = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        String qLike = (q != null && !q.isBlank()) ? "%" + q.trim() + "%" : null;
        return orderRepository.findByFilters(effectiveUserId, status, from, to, qLike, pageable)
                .map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getOrder(Long orderId, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));

        if (!isPrivileged(currentUser.role()) && !order.getUser().getId().equals(currentUser.id())) {
            throw new AccessDeniedException("无权查看此订单");
        }
        List<ForwardingParcelEntity> linked = parcelRepository.findByLinkedOrderId(orderId);
        return OrderDetailDto.from(order, linked);
    }

    @Transactional
    public OrderDto updateNotes(Long orderId, String notes, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!isPrivileged(currentUser.role()) && !order.getUser().getId().equals(currentUser.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权修改此订单");
        }
        order.setNotes(notes);
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public OrderDto updateStatus(Long orderId, UpdateOrderStatusRequest req) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.setStatus(req.status());
        if (req.notes() != null) {
            order.setNotes(req.notes());
        }
        if (req.transitTrackingNo() != null) {
            if (order.getTransitTrackingNo() != null && !order.getTransitTrackingNo().isBlank()) {
                throw new AppException(HttpStatus.FORBIDDEN, "出库单号已填写，不可修改");
            }
            order.setTransitTrackingNo(req.transitTrackingNo());
        }
        if (req.transitCarrier() != null) {
            if (order.getTransitCarrier() != null && !order.getTransitCarrier().isBlank()) {
                throw new AppException(HttpStatus.FORBIDDEN, "出库物流商已填写，不可修改");
            }
            order.setTransitCarrier(req.transitCarrier());
        }
        orderRepository.save(order);

        // When order ships, also ship any linked forwarding parcels with the same tracking
        if (order.getStatus() == OrderEntity.OrderStatus.SHIPPED && order.getTransitTrackingNo() != null) {
            List<ForwardingParcelEntity> linked = parcelRepository.findByLinkedOrderId(order.getId());
            for (ForwardingParcelEntity parcel : linked) {
                if (parcel.getStatus() == ForwardingParcelEntity.ParcelStatus.PACKING
                        || parcel.getStatus() == ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE) {
                    parcel.setStatus(ForwardingParcelEntity.ParcelStatus.SHIPPED);
                    parcel.setOutboundTrackingNo(order.getTransitTrackingNo());
                }
            }
            parcelRepository.saveAll(linked);
            // 发货邮件通知
            String userEmail = order.getUser().getEmail();
            if (userEmail != null && !userEmail.isBlank()) {
                emailService.sendShippedEmail(userEmail, displayName(order.getUser()),
                        order.getOrderNo(), order.getTransitTrackingNo(), order.getTransitCarrier());
            }
        }

        return OrderDto.from(order);
    }

    @Transactional
    public OrderDetailDto adminUpdateOrder(Long orderId, OrderEntity.OrderStatus status,
                                           String transitTrackingNo, String transitCarrier) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (status != null) order.setStatus(status);
        if (transitTrackingNo != null) {
            String newTracking = transitTrackingNo.isBlank() ? null : transitTrackingNo.trim();
            if (newTracking != null) {
                orderItemRepository.findByItemTrackingNo(newTracking).ifPresent(i -> {
                    throw new AppException(HttpStatus.CONFLICT, "单号 " + newTracking + " 已被商品运单号使用");
                });
                parcelRepository.findByInboundTrackingNo(newTracking).ifPresent(p -> {
                    throw new AppException(HttpStatus.CONFLICT, "单号 " + newTracking + " 已在包裹系统中登记");
                });
                orderRepository.findByTransitTrackingNo(newTracking).ifPresent(existing -> {
                    if (!existing.getId().equals(orderId)) {
                        throw new AppException(HttpStatus.CONFLICT, "单号 " + newTracking + " 已被订单 " + existing.getOrderNo() + " 使用");
                    }
                });
            }
            order.setTransitTrackingNo(newTracking);
        }
        if (transitCarrier != null)
            order.setTransitCarrier(transitCarrier.isBlank() ? null : transitCarrier.trim());
        orderRepository.save(order);
        if (order.getStatus() == OrderEntity.OrderStatus.SHIPPED && order.getTransitTrackingNo() != null) {
            List<ForwardingParcelEntity> linked = parcelRepository.findByLinkedOrderId(order.getId());
            for (ForwardingParcelEntity parcel : linked) {
                if (parcel.getStatus() == ForwardingParcelEntity.ParcelStatus.PACKING
                        || parcel.getStatus() == ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE) {
                    parcel.setStatus(ForwardingParcelEntity.ParcelStatus.SHIPPED);
                    parcel.setOutboundTrackingNo(order.getTransitTrackingNo());
                }
            }
            parcelRepository.saveAll(linked);
            // 发货邮件通知
            String userEmail = order.getUser().getEmail();
            if (userEmail != null && !userEmail.isBlank()) {
                emailService.sendShippedEmail(userEmail, displayName(order.getUser()),
                        order.getOrderNo(), order.getTransitTrackingNo(), order.getTransitCarrier());
            }
        }
        List<ForwardingParcelEntity> linkedParcels = parcelRepository.findByLinkedOrderId(orderId);
        return OrderDetailDto.from(order, linkedParcels);
    }

    @Transactional
    public OrderDetailDto setServiceFee(Long orderId, Integer serviceFeeJpy, String serviceFeeMemo) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.setServiceFeeJpy(serviceFeeJpy != null ? serviceFeeJpy : 0);
        order.setServiceFeeMemo(serviceFeeMemo);
        order.setStatus(OrderEntity.OrderStatus.FEE_QUOTED);
        orderRepository.save(order);
        // 待付款邮件通知（代购费报价）
        String userEmail = order.getUser().getEmail();
        if (userEmail != null && !userEmail.isBlank()) {
            String feeDetails = null;
            if (serviceFeeJpy != null && serviceFeeJpy > 0) {
                feeDetails = "  代购服务费：¥" + serviceFeeJpy + " JPY"
                        + (serviceFeeMemo != null && !serviceFeeMemo.isBlank() ? "（" + serviceFeeMemo + "）" : "");
            }
            emailService.sendPaymentReminderEmail(userEmail, displayName(order.getUser()),
                    order.getOrderNo(), feeDetails);
        }
        List<ForwardingParcelEntity> linkedParcels = parcelRepository.findByLinkedOrderId(orderId);
        return OrderDetailDto.from(order, linkedParcels);
    }

    @Transactional
    public OrderDetailDto saveShippingQuote(Long orderId, String quotedRoute, Integer quotedFeeJpy) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.setQuotedRoute(quotedRoute != null && !quotedRoute.isBlank() ? quotedRoute.trim() : null);
        order.setQuotedFeeJpy(quotedFeeJpy);
        orderRepository.save(order);
        List<ForwardingParcelEntity> linkedParcels = parcelRepository.findByLinkedOrderId(orderId);
        return OrderDetailDto.from(order, linkedParcels);
    }

    @Transactional
    public OrderDetailDto savePackingPhotoOnly(Long orderId, String packingPhotoUrl) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.setPackingPhotoUrl(packingPhotoUrl != null && !packingPhotoUrl.isBlank() ? packingPhotoUrl.trim() : null);
        orderRepository.save(order);
        List<ForwardingParcelEntity> linkedParcels = parcelRepository.findByLinkedOrderId(orderId);
        return OrderDetailDto.from(order, linkedParcels);
    }

    @Transactional
    public OrderDetailDto savePackingInfo(Long orderId, Integer weightG, Integer lengthCm, Integer widthCm,
                                          Integer heightCm, String packingPhotoUrl) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getStatus() != OrderEntity.OrderStatus.PACKING) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只有打包中的订单可以填写打包信息");
        }
        if (weightG != null) order.setWeightG(weightG);
        if (lengthCm != null) order.setLengthCm(lengthCm);
        if (widthCm != null) order.setWidthCm(widthCm);
        if (heightCm != null) order.setHeightCm(heightCm);
        if (packingPhotoUrl != null) order.setPackingPhotoUrl(packingPhotoUrl.isBlank() ? null : packingPhotoUrl.trim());
        order.setStatus(OrderEntity.OrderStatus.AWAITING_PAYMENT);
        orderRepository.save(order);
        // 待付款邮件通知
        String userEmail = order.getUser().getEmail();
        if (userEmail != null && !userEmail.isBlank()) {
            String feeDetails = null;
            if (order.getQuotedFeeJpy() != null && order.getQuotedFeeJpy() > 0) {
                feeDetails = "  运费：¥" + order.getQuotedFeeJpy() + " JPY"
                        + (order.getQuotedRoute() != null ? "（" + order.getQuotedRoute() + "）" : "");
            }
            emailService.sendPaymentReminderEmail(userEmail, displayName(order.getUser()),
                    order.getOrderNo(), feeDetails);
        }
        List<ForwardingParcelEntity> linkedParcels = parcelRepository.findByLinkedOrderId(orderId);
        return OrderDetailDto.from(order, linkedParcels);
    }

    @Transactional
    public OrderDto adminUpdateOrderItem(Long orderId, Long itemId, int quantity, BigDecimal priceCny) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        OrderItemEntity item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!item.getOrder().getId().equals(orderId))
            throw new AppException(HttpStatus.BAD_REQUEST, "商品不属于该订单");
        item.setQuantity(quantity);
        item.setPriceCny(priceCny);
        orderItemRepository.save(item);
        BigDecimal newTotal = order.getItems().stream()
                .map(i -> i.getPriceCny().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalCny(newTotal);
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public OrderItemDto updateItemTracking(Long orderId, Long itemId, UpdateItemTrackingRequest req) {
        return updateItemTracking(orderId, itemId, req, false);
    }

    @Transactional
    public OrderItemDto updateItemTracking(Long orderId, Long itemId, UpdateItemTrackingRequest req, boolean privileged) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        OrderItemEntity item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!item.getOrder().getId().equals(orderId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "商品不属于该订单");
        }
        if (!privileged) {
            if ("IN_WAREHOUSE".equals(item.getItemStatus())) {
                throw new AppException(HttpStatus.FORBIDDEN, "已入库的商品不可修改");
            }
            if (item.getItemTrackingNo() != null && !item.getItemTrackingNo().isBlank()) {
                throw new AppException(HttpStatus.FORBIDDEN, "已绑定物流单号的商品不可修改");
            }
        }
        if (req.itemStatus() != null) item.setItemStatus(req.itemStatus());
        if (req.itemTrackingNo() != null) {
            String newTracking = req.itemTrackingNo().trim();
            if (!newTracking.isBlank()) {
                // Reject if tracking already used by another item
                orderItemRepository.findByItemTrackingNo(newTracking).ifPresent(existing -> {
                    if (!existing.getId().equals(itemId)) {
                        throw new AppException(HttpStatus.CONFLICT, "运单号 " + newTracking + " 已被其他商品使用");
                    }
                });
                // Reject if tracking already registered as a parcel inbound tracking
                parcelRepository.findByInboundTrackingNo(newTracking).ifPresent(parcel -> {
                    throw new AppException(HttpStatus.CONFLICT, "运单号 " + newTracking + " 已在包裹系统中登记");
                });
            }
            item.setItemTrackingNo(newTracking.isBlank() ? null : newTracking);
        }
        if (req.itemCarrier() != null) item.setItemCarrier(req.itemCarrier());
        orderItemRepository.save(item);

        // Register tracking number with HBR
        if (req.itemTrackingNo() != null && !req.itemTrackingNo().isBlank()) {
            hbrService.createConsolidatedOrderForItem(req.itemTrackingNo().trim(), req.itemCarrier(), item);
        }

        // Auto-advance order to IN_WAREHOUSE only when all items are physically checked in
        if ("IN_WAREHOUSE".equals(item.getItemStatus())) {
            long total = orderItemRepository.countByOrderId(orderId);
            long inWarehouse = orderItemRepository.countByOrderIdAndItemStatus(orderId, "IN_WAREHOUSE");
            if (total > 0 && total == inWarehouse
                    && order.getStatus() == OrderEntity.OrderStatus.PURCHASING) {
                order.setStatus(OrderEntity.OrderStatus.IN_WAREHOUSE);
                orderRepository.save(order);
            }
        }

        return OrderItemDto.from(item);
    }

    @Transactional
    public CheckinResult checkinItemByTrackingNo(String trackingNo) {
        String no = trackingNo != null ? trackingNo.trim() : "";
        return orderItemRepository.findByItemTrackingNo(no)
                .map(item -> {
                    if ("IN_WAREHOUSE".equals(item.getItemStatus())) {
                        return new CheckinResult(false, "该商品已入库", no, "IN_WAREHOUSE",
                                displayName(item.getOrder().getUser()), null, null);
                    }
                    item.setItemStatus("IN_WAREHOUSE");
                    orderItemRepository.save(item);

                    // Auto-advance order to IN_WAREHOUSE if all items are now checked in
                    OrderEntity order = item.getOrder();
                    if (order.getStatus() == OrderEntity.OrderStatus.PURCHASING) {
                        long total = orderItemRepository.countByOrderId(order.getId());
                        long inWarehouse = orderItemRepository.countByOrderIdAndItemStatus(order.getId(), "IN_WAREHOUSE");
                        if (total > 0 && total == inWarehouse) {
                            order.setStatus(OrderEntity.OrderStatus.IN_WAREHOUSE);
                            orderRepository.save(order);
                        }
                    }

                    return new CheckinResult(true, "商品入库成功: " + item.getProductTitle(),
                            no, "IN_WAREHOUSE", displayName(item.getOrder().getUser()), null, null);
                })
                .orElse(null);
    }

    @Transactional
    public OrderDto advanceToPackingIfReady(Long orderId, List<Long> parcelIds, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!isPrivileged(currentUser.role()) && !order.getUser().getId().equals(currentUser.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权操作此订单");
        }
        long total = orderItemRepository.countByOrderId(orderId);
        long inWarehouse = orderItemRepository.countByOrderIdAndItemStatus(orderId, "IN_WAREHOUSE");
        if (total == 0 || total != inWarehouse) {
            throw new AppException(HttpStatus.BAD_REQUEST, "还有商品未入库 (" + inWarehouse + "/" + total + ")");
        }
        // Link selected forwarding parcels to this order
        if (parcelIds != null && !parcelIds.isEmpty()) {
            for (Long parcelId : parcelIds) {
                parcelRepository.findById(parcelId).ifPresent(parcel -> {
                    if (!parcel.getUser().getId().equals(order.getUser().getId())) {
                        throw new AppException(HttpStatus.FORBIDDEN, "包裹不属于该用户");
                    }
                    if (parcel.getStatus() != ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "包裹 #" + parcelId + " 尚未入库");
                    }
                    parcel.setLinkedOrder(order);
                    parcel.setStatus(ForwardingParcelEntity.ParcelStatus.PACKING);
                    parcelRepository.save(parcel);
                });
            }
        }
        return advanceOrderToPackingById(orderId);
    }

    @Transactional(readOnly = true)
    public List<ParcelDto> getLinkableParcels(Long orderId, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权操作此订单");
        }
        return parcelRepository.findByUserIdAndStatusIn(currentUser.id(),
                        List.of(ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE, ForwardingParcelEntity.ParcelStatus.ANNOUNCED))
                .stream().map(ParcelDto::from).toList();
    }

    private OrderDto advanceOrderToPackingById(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getStatus() == OrderEntity.OrderStatus.PACKING
                || order.getStatus() == OrderEntity.OrderStatus.SHIPPED
                || order.getStatus() == OrderEntity.OrderStatus.DELIVERED) {
            return OrderDto.from(order);
        }
        order.setStatus(OrderEntity.OrderStatus.PACKING);
        if (order.getPackingNo() == null) order.setPackingNo(generatePackingNo());
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public OrderDto updateItem(Long orderId, Long itemId, UpdateOrderItemRequest req, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权修改此订单");
        }
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能修改待付款的订单");
        }
        OrderItemEntity item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!item.getOrder().getId().equals(orderId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "商品不属于该订单");
        }
        item.setQuantity(req.quantity());
        item.setPriceCny(req.priceCny());
        orderItemRepository.save(item);

        // Recalculate order total
        java.math.BigDecimal newTotal = order.getItems().stream()
                .map(i -> i.getPriceCny().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        order.setTotalCny(newTotal);
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    public record CheckinResult(boolean success, String message, String orderNo, String orderStatus, String userDisplay, String inboundCode, Long parcelId) {}

    @Transactional
    public CheckinResult checkinByOrderNo(String orderNo) {
        String no = orderNo != null ? orderNo.trim() : "";
        return orderRepository.findByOrderNo(no)
                .map(order -> {
                    if (order.getStatus() != OrderEntity.OrderStatus.PURCHASING) {
                        return new CheckinResult(false,
                                "状态不符（当前: " + order.getStatus().name() + "）",
                                no, order.getStatus().name(), displayName(order.getUser()), null, null);
                    }
                    order.setStatus(OrderEntity.OrderStatus.IN_WAREHOUSE);
                    orderRepository.save(order);
                    return new CheckinResult(true, "入库成功",
                            order.getOrderNo(), "IN_WAREHOUSE", displayName(order.getUser()), null, null);
                })
                .orElse(new CheckinResult(false, "未找到匹配订单", no, null, null, null, null));
    }

    @Transactional
    public OrderDto mergePendingOrders(Long userId) {
        List<OrderEntity> pending = orderRepository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, OrderEntity.OrderStatus.PENDING_PAYMENT);
        if (pending.size() <= 1) {
            return pending.isEmpty() ? null : OrderDto.from(pending.get(0));
        }

        OrderEntity target = pending.get(0);
        for (int i = 1; i < pending.size(); i++) {
            OrderEntity source = pending.get(i);
            for (OrderItemEntity sourceItem : new java.util.ArrayList<>(source.getItems())) {
                OrderItemEntity existing = target.getItems().stream()
                        .filter(t -> sourceItem.getOriginalUrl() != null && sourceItem.getOriginalUrl().equals(t.getOriginalUrl()))
                        .findFirst().orElse(null);
                if (existing != null) {
                    existing.setQuantity(existing.getQuantity() + sourceItem.getQuantity());
                } else {
                    OrderItemEntity newItem = new OrderItemEntity();
                    newItem.setOrder(target);
                    newItem.setProductTitle(sourceItem.getProductTitle());
                    newItem.setOriginalUrl(sourceItem.getOriginalUrl());
                    newItem.setPriceJpy(sourceItem.getPriceJpy());
                    newItem.setPriceCny(sourceItem.getPriceCny());
                    newItem.setQuantity(sourceItem.getQuantity());
                    newItem.setRemarks(sourceItem.getRemarks());
                    newItem.setDomesticShipping(sourceItem.getDomesticShipping());
                    newItem.setExchangeRate(sourceItem.getExchangeRate());
                    newItem.setPlatform(sourceItem.getPlatform());
                    newItem.setImageUrl(sourceItem.getImageUrl());
                    target.getItems().add(newItem);
                }
            }
            orderRepository.delete(source);
        }

        java.math.BigDecimal newTotal = target.getItems().stream()
                .map(i -> i.getPriceCny().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        target.setTotalCny(newTotal);
        orderRepository.save(target);
        return OrderDto.from(target);
    }

    @Transactional
    public OrderDto addToPendingOrder(CreateOrderRequest req, Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));

        OrderEntity order = orderRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderEntity.OrderStatus.PENDING_PAYMENT)
                .orElse(null);

        if (order == null) {
            order = new OrderEntity();
            order.setUser(user);
            order.setOrderNo(generateOrderNo());
            order.setTotalCny(java.math.BigDecimal.ZERO);
            order.setNotes(req.notes());
        }

        for (OrderItemRequest itemReq : req.items()) {
            OrderItemEntity existing = order.getItems().stream()
                    .filter(i -> itemReq.originalUrl() != null && itemReq.originalUrl().equals(i.getOriginalUrl()))
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + itemReq.quantity());
            } else {
                OrderItemEntity item = new OrderItemEntity();
                item.setOrder(order);
                item.setProductTitle(itemReq.productTitle());
                item.setOriginalUrl(itemReq.originalUrl());
                item.setPriceJpy(itemReq.priceJpy());
                item.setPriceCny(itemReq.priceCny());
                item.setQuantity(itemReq.quantity());
                item.setRemarks(itemReq.remarks());
                item.setDomesticShipping(itemReq.domesticShipping() != null ? itemReq.domesticShipping() : java.math.BigDecimal.ZERO);
                item.setExchangeRate(itemReq.exchangeRate());
                item.setPlatform(itemReq.platform());
                item.setImageUrl(itemReq.imageUrl());
                item.setReferenceImages(serializeReferenceImages(itemReq.referenceImages()));
                order.getItems().add(item);
            }
        }

        java.math.BigDecimal newTotal = order.getItems().stream()
                .map(i -> i.getPriceCny().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        order.setTotalCny(newTotal);
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public OrderDto deleteOrderItem(Long orderId, Long itemId, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权修改此订单");
        }
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能修改待付款的订单");
        }
        OrderItemEntity item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!item.getOrder().getId().equals(orderId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "商品不属于该订单");
        }
        order.getItems().remove(item);
        orderItemRepository.delete(item);

        if (order.getItems().isEmpty()) {
            orderRepository.delete(order);
            return null;
        }

        java.math.BigDecimal newTotal = order.getItems().stream()
                .map(i -> i.getPriceCny().multiply(java.math.BigDecimal.valueOf(i.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        order.setTotalCny(newTotal);
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public void deleteOrder(Long orderId, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权删除此订单");
        }
        if (order.getStatus() != OrderEntity.OrderStatus.PENDING_PAYMENT) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只能删除待付款的订单");
        }
        orderRepository.delete(order);
    }

    @Transactional
    public OrderDto cancelPacking(Long orderId, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getUser().getId().equals(currentUser.id()) && !isPrivileged(currentUser.role())) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权操作此订单");
        }
        if (order.getStatus() != OrderEntity.OrderStatus.PACKING) {
            throw new AppException(HttpStatus.BAD_REQUEST, "只有打包中的订单可以撤销");
        }
        // Unlink and revert forwarding parcels back to IN_WAREHOUSE
        List<ForwardingParcelEntity> parcels = parcelRepository.findByLinkedOrderId(orderId);
        for (ForwardingParcelEntity parcel : parcels) {
            parcel.setLinkedOrder(null);
            parcel.setStatus(ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE);
        }
        parcelRepository.saveAll(parcels);
        // Revert order to IN_WAREHOUSE
        order.setStatus(OrderEntity.OrderStatus.IN_WAREHOUSE);
        order.setWeightG(null);
        order.setLengthCm(null);
        order.setWidthCm(null);
        order.setHeightCm(null);
        order.setPackingPhotoUrl(null);
        return OrderDto.from(orderRepository.save(order));
    }

    @Transactional
    public List<OrderDto> createPackingRequest(List<Long> orderItemIds, List<Long> parcelIds, AuthenticatedUser currentUser) {
        // Collect unique order IDs from selected items
        Set<Long> orderIdSet = new HashSet<>();
        if (orderItemIds != null) {
            for (Long itemId : orderItemIds) {
                orderItemRepository.findById(itemId).ifPresent(item -> orderIdSet.add(item.getOrder().getId()));
            }
        }

        boolean hasParcels = parcelIds != null && !parcelIds.isEmpty();
        if (orderIdSet.isEmpty() && !hasParcels) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请至少选择一个订单或转运包裹");
        }

        List<OrderEntity> orders = new ArrayList<>(orderRepository.findAllById(orderIdSet));
        for (OrderEntity order : orders) {
            if (!order.getUser().getId().equals(currentUser.id())) {
                throw new AppException(HttpStatus.FORBIDDEN, "无权操作此订单");
            }
        }

        // Advance each order to PACKING
        for (OrderEntity order : orders) {
            if (order.getStatus() == OrderEntity.OrderStatus.PURCHASING
                    || order.getStatus() == OrderEntity.OrderStatus.IN_WAREHOUSE) {
                order.setStatus(OrderEntity.OrderStatus.PACKING);
                if (order.getPackingNo() == null) order.setPackingNo(generatePackingNo());
            }
        }
        orderRepository.saveAll(orders);

        // If multiple proxy orders selected, merge all items into the earliest (primary) order
        if (orders.size() > 1) {
            OrderEntity primaryOrder = orders.stream()
                    .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .orElse(orders.get(0));
            for (OrderEntity order : orders) {
                if (order.getId().equals(primaryOrder.getId())) continue;
                // Move items via direct JPQL update to avoid orphanRemoval deleting them
                orderItemRepository.moveItemsToOrder(order.getId(), primaryOrder.getId());
                // Add secondary order's total to primary
                if (order.getTotalCny() != null) {
                    primaryOrder.setTotalCny(primaryOrder.getTotalCny() == null
                            ? order.getTotalCny()
                            : primaryOrder.getTotalCny().add(order.getTotalCny()));
                }
                // Cancel secondary order (mark notes, zero out total)
                order.setStatus(OrderEntity.OrderStatus.CANCELLED);
                order.setNotes("已合并到 " + primaryOrder.getOrderNo());
                order.setTotalCny(BigDecimal.ZERO);
                // Clear in-memory items so orphanRemoval doesn't delete them on save
                order.getItems().clear();
                orderRepository.save(order);
            }
            orderRepository.save(primaryOrder);
            // Keep only primary order for parcel linking and response
            orders = List.of(primaryOrder);
        }

        // Link selected parcels together — always grouped under one primary order
        if (hasParcels) {
            OrderEntity primaryOrder = orders.isEmpty() ? null : orders.stream()
                    .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .orElse(orders.get(0));

            // No proxy orders selected: create a consolidation order to group all parcels together
            if (primaryOrder == null) {
                UserEntity user = userRepository.findById(currentUser.id())
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
                OrderEntity consolidation = new OrderEntity();
                consolidation.setUser(user);
                consolidation.setOrderNo(generateOrderNo());
                consolidation.setTotalCny(java.math.BigDecimal.ZERO);
                consolidation.setStatus(OrderEntity.OrderStatus.PACKING);
                consolidation.setNotes("合箱转运");
                primaryOrder = orderRepository.save(consolidation);
                orders.add(primaryOrder);
            }

            List<ForwardingParcelEntity> parcels = parcelRepository.findAllById(parcelIds);
            for (ForwardingParcelEntity parcel : parcels) {
                if (!parcel.getUser().getId().equals(currentUser.id())) continue;
                if (parcel.getStatus() == ForwardingParcelEntity.ParcelStatus.IN_WAREHOUSE) {
                    parcel.setLinkedOrder(primaryOrder);
                    parcel.setStatus(ForwardingParcelEntity.ParcelStatus.PACKING);
                }
            }
            parcelRepository.saveAll(parcels);
        }

        return orders.stream().map(OrderDto::from).toList();
    }

    private String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "CG-" + dateStr + "-";
        long count = orderRepository.countByOrderNoPrefix(prefix);
        return prefix + String.format("%04d", count + 1);
    }

    private String generatePackingNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "HX-" + dateStr + "-";
        long count = orderRepository.countByPackingNoPrefix(prefix);
        return prefix + String.format("%04d", count + 1);
    }

    private String displayName(com.zilai.zilaibuy.entity.UserEntity user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
        return user.getPhone();
    }

    private boolean isPrivileged(String role) {
        return "SUPPORT".equals(role) || "ADMIN".equals(role) || "WAREHOUSE".equals(role);
    }
}
