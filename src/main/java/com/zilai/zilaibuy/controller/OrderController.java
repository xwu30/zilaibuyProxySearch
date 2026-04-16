package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.order.*;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.service.AuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.dto.order.UpdateOrderItemRequest;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.time.Duration;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AuditLogService auditLogService;

    @Value("${app.s3.bucket:zilaibuy-media}")
    private String s3Bucket;

    @Value("${app.s3.region:us-east-1}")
    private String s3Region;

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
            @RequestParam(required = false) java.time.LocalDate dateFrom,
            @RequestParam(required = false) java.time.LocalDate dateTo,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(orderService.listOrders(currentUser, userId, status, dateFrom, dateTo, q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailDto> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.getOrder(id, currentUser));
    }

    /** Step 1: get a presigned S3 PUT URL for direct browser→S3 upload (bypasses WAF) */
    @GetMapping("/{id}/reference-images/presign")
    public ResponseEntity<Map<String, String>> presignReferenceImage(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String key = "reference-images/" + currentUser.id() + "/" + id + "/" + UUID.randomUUID() + ".jpg";
        try (S3Presigner presigner = S3Presigner.builder().region(Region.of(s3Region)).build()) {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(15))
                            .putObjectRequest(PutObjectRequest.builder()
                                    .bucket(s3Bucket).key(key).contentType("image/jpeg").build())
                            .build());
            String publicUrl = "https://" + s3Bucket + ".s3." + s3Region + ".amazonaws.com/" + key;
            return ResponseEntity.ok(Map.of("uploadUrl", presigned.url().toString(), "publicUrl", publicUrl));
        }
    }

    /** Step 2: after S3 upload succeeds, save the public URL into the order item */
    @PostMapping(value = "/{id}/reference-images", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> saveReferenceImageUrl(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String originalUrl = body.get("originalUrl");
        String imageUrl = body.get("imageUrl");
        if (originalUrl != null && imageUrl != null) {
            orderService.saveReferenceImageDirect(id, originalUrl, imageUrl, currentUser.id());
        }
        return ResponseEntity.ok().build();
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
            @Valid @RequestBody UpdateOrderStatusRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderDto result = orderService.updateStatus(id, req);
        String detail = "{\"status\":\"" + req.status().name() + "\""
                + (req.transitTrackingNo() != null ? ",\"tracking\":\"" + req.transitTrackingNo() + "\"" : "")
                + "}";
        auditLogService.log(currentUser.id(), "ORDER_STATUS_CHANGED", "ORDER", String.valueOf(id),
                detail, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/advance-packing")
    public ResponseEntity<OrderDto> advanceToPacking(
            @PathVariable Long orderId,
            @RequestBody(required = false) AdvancePackingRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<Long> parcelIds = req != null ? req.parcelIds() : null;
        return ResponseEntity.ok(orderService.advanceToPackingIfReady(orderId, parcelIds, currentUser));
    }

    @GetMapping("/{orderId}/linkable-parcels")
    public ResponseEntity<List<ParcelDto>> getLinkableParcels(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.getLinkableParcels(orderId, currentUser));
    }

    @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    @PatchMapping("/{orderId}/items/{itemId}/tracking")
    public ResponseEntity<OrderItemDto> updateItemTracking(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody UpdateItemTrackingRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        boolean privileged = "ADMIN".equals(currentUser.role()) || "WAREHOUSE".equals(currentUser.role()) || "SUPPORT".equals(currentUser.role());
        OrderItemDto result = orderService.updateItemTracking(orderId, itemId, req, privileged);
        if (req.itemTrackingNo() != null && !req.itemTrackingNo().isBlank()) {
            String detail = String.format("{\"carrier\":\"%s\",\"trackingNo\":\"%s\"}",
                    req.itemCarrier() != null ? req.itemCarrier() : "",
                    req.itemTrackingNo().trim());
            auditLogService.log(currentUser.id(), "ITEM_TRACKING_SET", "ORDER_ITEM",
                    orderId + "-" + itemId, detail, httpReq.getRemoteAddr());
        }
        return ResponseEntity.ok(result);
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
        OrderDto result = orderService.deleteOrderItem(orderId, itemId, currentUser);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    @PostMapping("/packing-request")
    public ResponseEntity<List<OrderDto>> createPackingRequest(
            @RequestBody PackingRequestBody req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(orderService.createPackingRequest(req.orderItemIds(), req.parcelIds(), currentUser));
    }

    @PostMapping("/{id}/cancel-packing")
    public ResponseEntity<OrderDto> cancelPacking(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderDto result = orderService.cancelPacking(id, currentUser);
        auditLogService.log(currentUser.id(), "ORDER_PACKING_CANCELLED", "ORDER", String.valueOf(id),
                null, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        orderService.deleteOrder(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
