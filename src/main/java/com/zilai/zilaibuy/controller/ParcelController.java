package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.parcel.CreateParcelRequest;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.ForwardingParcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ForwardingParcelService parcelService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ParcelDto> create(
            @Valid @RequestBody CreateParcelRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.createParcel(req, currentUser.id()));
    }

    @GetMapping
    public ResponseEntity<List<ParcelDto>> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.listUserParcels(currentUser.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParcelDto> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateParcelRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.updateParcel(id, req, currentUser.id()));
    }

    @PatchMapping("/{id}/notes")
    public ResponseEntity<ParcelDto> updateNotes(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.updateNotes(id, body.get("notes"), currentUser.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        parcelService.deleteParcel(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check-tracking")
    public ResponseEntity<Map<String, Boolean>> checkTracking(
            @RequestParam String trackingNo,
            @RequestParam(required = false) Long excludeId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        boolean exists = parcelService.trackingNumberExists(trackingNo.trim(), excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    record ShippingRequestBody(List<Long> parcelIds, String shippingLine, double totalCny, boolean addInspection, boolean addPhoto) {}
    record ShippingRequestResponse(long orderId, String orderNo) {}

    @PostMapping("/shipping-request")
    public ResponseEntity<ShippingRequestResponse> createShippingRequest(
            @RequestBody ShippingRequestBody req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new RuntimeException("User not found"));

        OrderEntity order = new OrderEntity();
        order.setUser(user);
        order.setOrderNo("SH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setTotalCny(BigDecimal.valueOf(req.totalCny()));
        order.setStatus(OrderEntity.OrderStatus.PENDING_PAYMENT);
        String notes = String.format("转运申请 | 线路: %s | 验货: %s | 拍照: %s | 包裹数: %d",
                req.shippingLine(),
                req.addInspection() ? "是" : "否",
                req.addPhoto() ? "是" : "否",
                req.parcelIds().size());
        order.setNotes(notes);

        OrderEntity saved = orderRepository.save(order);
        return ResponseEntity.ok(new ShippingRequestResponse(saved.getId(), saved.getOrderNo()));
    }
}
