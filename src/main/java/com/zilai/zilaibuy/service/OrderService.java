package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.order.*;
import com.zilai.zilaibuy.dto.order.UpdateItemTrackingRequest;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.OrderItemEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
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

import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

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
            order.getItems().add(item);
        }

        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> listOrders(AuthenticatedUser currentUser, Long filterUserId,
                                     OrderEntity.OrderStatus status, Pageable pageable) {
        boolean isPrivileged = isPrivileged(currentUser.role());
        Long effectiveUserId = isPrivileged ? filterUserId : currentUser.id();
        return orderRepository.findByFilters(effectiveUserId, status, pageable)
                .map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getOrder(Long orderId, AuthenticatedUser currentUser) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));

        if (!isPrivileged(currentUser.role()) && !order.getUser().getId().equals(currentUser.id())) {
            throw new AccessDeniedException("无权查看此订单");
        }
        return OrderDetailDto.from(order);
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
            order.setTransitTrackingNo(req.transitTrackingNo());
        }
        orderRepository.save(order);
        return OrderDto.from(order);
    }

    @Transactional
    public OrderItemDto updateItemTracking(Long orderId, Long itemId, UpdateItemTrackingRequest req) {
        orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        OrderItemEntity item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!item.getOrder().getId().equals(orderId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "商品不属于该订单");
        }
        if ("IN_WAREHOUSE".equals(item.getItemStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "已入库的商品不可修改");
        }
        if (item.getItemTrackingNo() != null && !item.getItemTrackingNo().isBlank()) {
            throw new AppException(HttpStatus.FORBIDDEN, "已绑定物流单号的商品不可修改");
        }
        if (req.itemStatus() != null) item.setItemStatus(req.itemStatus());
        if (req.itemTrackingNo() != null) item.setItemTrackingNo(req.itemTrackingNo());
        if (req.itemCarrier() != null) item.setItemCarrier(req.itemCarrier());
        orderItemRepository.save(item);
        return OrderItemDto.from(item);
    }

    @Transactional
    public CheckinResult checkinItemByTrackingNo(String trackingNo) {
        String no = trackingNo != null ? trackingNo.trim() : "";
        return orderItemRepository.findByItemTrackingNo(no)
                .map(item -> {
                    if ("IN_WAREHOUSE".equals(item.getItemStatus())) {
                        return new CheckinResult(false, "该商品已入库", no, "IN_WAREHOUSE",
                                item.getOrder().getUser().getPhone());
                    }
                    item.setItemStatus("IN_WAREHOUSE");
                    orderItemRepository.save(item);

                    // Auto-advance order to PACKING if all items are now IN_WAREHOUSE
                    OrderEntity order = item.getOrder();
                    boolean allIn = order.getItems().stream()
                            .allMatch(i -> "IN_WAREHOUSE".equals(i.getItemStatus()));
                    if (allIn && order.getStatus() != OrderEntity.OrderStatus.PACKING
                            && order.getStatus() != OrderEntity.OrderStatus.SHIPPED
                            && order.getStatus() != OrderEntity.OrderStatus.DELIVERED) {
                        order.setStatus(OrderEntity.OrderStatus.PACKING);
                        orderRepository.save(order);
                    }

                    return new CheckinResult(true, "商品入库成功: " + item.getProductTitle(),
                            no, "IN_WAREHOUSE", item.getOrder().getUser().getPhone());
                })
                .orElse(null);
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

    public record CheckinResult(boolean success, String message, String orderNo, String orderStatus, String userPhone) {}

    @Transactional
    public CheckinResult checkinByOrderNo(String orderNo) {
        String no = orderNo != null ? orderNo.trim() : "";
        return orderRepository.findByOrderNo(no)
                .map(order -> {
                    if (order.getStatus() != OrderEntity.OrderStatus.PURCHASING) {
                        return new CheckinResult(false,
                                "状态不符（当前: " + order.getStatus().name() + "）",
                                no, order.getStatus().name(), order.getUser().getPhone());
                    }
                    order.setStatus(OrderEntity.OrderStatus.IN_WAREHOUSE);
                    orderRepository.save(order);
                    return new CheckinResult(true, "入库成功",
                            order.getOrderNo(), "IN_WAREHOUSE", order.getUser().getPhone());
                })
                .orElse(new CheckinResult(false, "未找到匹配订单", no, null, null));
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

    private String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "DG-" + dateStr + "-";
        long count = orderRepository.countByOrderNoPrefix(prefix);
        return prefix + String.format("%04d", count + 1);
    }

    private boolean isPrivileged(String role) {
        return "SUPPORT".equals(role) || "ADMIN".equals(role) || "WAREHOUSE".equals(role);
    }
}
