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
        }

        return OrderDto.from(order);
    }

    @Transactional
    public OrderDetailDto adminUpdateOrder(Long orderId, OrderEntity.OrderStatus status,
                                           String transitTrackingNo, String transitCarrier) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (status != null) order.setStatus(status);
        if (transitTrackingNo != null)
            order.setTransitTrackingNo(transitTrackingNo.isBlank() ? null : transitTrackingNo.trim());
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
                                displayName(item.getOrder().getUser()), null, null);
                    }
                    item.setItemStatus("IN_WAREHOUSE");
                    orderItemRepository.save(item);

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

    @Transactional
    public List<OrderDto> createPackingRequest(List<Long> orderItemIds, List<Long> parcelIds, AuthenticatedUser currentUser) {
        // Collect unique order IDs from selected items
        Set<Long> orderIdSet = new HashSet<>();
        if (orderItemIds != null) {
            for (Long itemId : orderItemIds) {
                orderItemRepository.findById(itemId).ifPresent(item -> orderIdSet.add(item.getOrder().getId()));
            }
        }
        if (orderIdSet.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "未选择代购商品");

        List<OrderEntity> orders = orderRepository.findAllById(orderIdSet);
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
            }
        }
        orderRepository.saveAll(orders);

        // Link selected parcels to the first (earliest) order
        if (parcelIds != null && !parcelIds.isEmpty()) {
            OrderEntity primaryOrder = orders.stream()
                    .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .orElse(orders.get(0));
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
        String prefix = "DG-" + dateStr + "-";
        long count = orderRepository.countByOrderNoPrefix(prefix);
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
