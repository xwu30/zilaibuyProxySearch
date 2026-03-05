package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.order.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.dto.order.UpdateOrderItemRequest;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(
            @Valid @RequestBody CreateOrderRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.createOrder(req, currentUser.id()));
    }

    @GetMapping
    public ResponseEntity<Page<OrderDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderEntity.OrderStatus status,
            @RequestParam(required = false) Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderService.listOrders(currentUser, userId, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailDto> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.getOrder(id, currentUser));
    }

    @PatchMapping("/{id}/notes")
    public ResponseEntity<OrderDto> updateNotes(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.updateNotes(id, body.get("notes"), currentUser));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest req) {
        return ResponseEntity.ok(orderService.updateStatus(id, req));
    }

    @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    @PatchMapping("/{orderId}/items/{itemId}/tracking")
    public ResponseEntity<OrderItemDto> updateItemTracking(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody UpdateItemTrackingRequest req) {
        return ResponseEntity.ok(orderService.updateItemTracking(orderId, itemId, req));
    }

    @PutMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderDto> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody UpdateOrderItemRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.updateItem(orderId, itemId, req, currentUser));
    }

    @PostMapping("/merge-pending")
    public ResponseEntity<OrderDto> mergePendingOrders(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OrderDto result = orderService.mergePendingOrders(currentUser.id());
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    @PostMapping("/pending/add")
    public ResponseEntity<OrderDto> addToPendingOrder(
            @Valid @RequestBody CreateOrderRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.addToPendingOrder(req, currentUser.id()));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderDto> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.deleteOrderItem(orderId, itemId, currentUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        orderService.deleteOrder(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
