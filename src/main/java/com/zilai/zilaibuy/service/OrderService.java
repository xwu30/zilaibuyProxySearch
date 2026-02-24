package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.order.*;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.OrderItemEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
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
    public OrderDto updateStatus(Long orderId, UpdateOrderStatusRequest req) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.setStatus(req.status());
        if (req.notes() != null) {
            order.setNotes(req.notes());
        }
        orderRepository.save(order);
        return OrderDto.from(order);
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
