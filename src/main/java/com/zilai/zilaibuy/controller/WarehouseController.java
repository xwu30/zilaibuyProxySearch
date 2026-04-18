package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.dto.warehouse.*;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.AuditLogService;
import com.zilai.zilaibuy.service.ForwardingParcelService;
import com.zilai.zilaibuy.service.InventoryService;
import com.zilai.zilaibuy.service.NewProductService;
import com.zilai.zilaibuy.service.OrderService;
import com.zilai.zilaibuy.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
public class WarehouseController {

    private final ProductService productService;
    private final NewProductService newProductService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final ForwardingParcelService parcelService;
    private final AuditLogService auditLogService;

    record CheckinRequest(String orderNo, String location) {}

    private OrderService.CheckinResult checkinAny(String no, String location) {
        OrderService.CheckinResult r = orderService.checkinByOrderNo(no);
        if (r.success() || !r.message().equals("未找到匹配订单")) return r;
        OrderService.CheckinResult pr = parcelService.checkinByTrackingNo(no, location);
        if (pr != null) return pr;
        OrderService.CheckinResult ir = orderService.checkinItemByTrackingNo(no);
        return ir != null ? ir : r;
    }

    @PostMapping("/checkin")
    public ResponseEntity<OrderService.CheckinResult> checkin(
            @RequestBody CheckinRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderService.CheckinResult result = checkinAny(req.orderNo(), req.location());
        if (result.success()) {
            auditLogService.log(currentUser.id(), "CHECKIN", result.parcelId() != null ? "PARCEL" : "ORDER",
                    result.parcelId() != null ? String.valueOf(result.parcelId()) : result.orderNo(),
                    "{\"no\":\"" + req.orderNo() + "\",\"user\":\"" + result.userDisplay() + "\"}",
                    httpReq.getRemoteAddr());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/checkin/batch")
    public ResponseEntity<java.util.List<OrderService.CheckinResult>> checkinBatch(
            @RequestBody java.util.List<CheckinRequest> reqs,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        java.util.List<OrderService.CheckinResult> results = reqs.stream()
                .map(r -> checkinAny(r.orderNo(), r.location())).toList();
        results.stream().filter(OrderService.CheckinResult::success).forEach(result ->
            auditLogService.log(currentUser.id(), "CHECKIN", result.parcelId() != null ? "PARCEL" : "ORDER",
                    result.parcelId() != null ? String.valueOf(result.parcelId()) : result.orderNo(),
                    "{\"user\":\"" + result.userDisplay() + "\"}",
                    httpReq.getRemoteAddr())
        );
        return ResponseEntity.ok(results);
    }

    @GetMapping("/parcels")
    public ResponseEntity<Page<ParcelDto>> listParcels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(parcelService.searchParcels(q, pageable));
        }
        if (status != null && !status.isBlank()) {
            var s = com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus.valueOf(status);
            return ResponseEntity.ok(parcelService.listAllParcelsByStatus(s, pageable));
        }
        return ResponseEntity.ok(parcelService.listAllParcels(pageable));
    }

    record UpdateWeightRequest(Double weightKg) {}

    @PatchMapping("/parcels/{id}/weight")
    public ResponseEntity<ParcelDto> updateWeight(
            @PathVariable Long id,
            @RequestBody UpdateWeightRequest req) {
        return ResponseEntity.ok(parcelService.updateWeight(id, req.weightKg()));
    }

    record ShipParcelRequest(String outboundTrackingNo, String notes) {}

    @PutMapping("/parcels/{id}/ship")
    public ResponseEntity<ParcelDto> shipParcel(
            @PathVariable Long id,
            @RequestBody ShipParcelRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        ParcelDto result = parcelService.shipParcel(id, req.outboundTrackingNo(), req.notes());
        auditLogService.log(currentUser.id(), "PARCEL_SHIPPED", "PARCEL", String.valueOf(id),
                "{\"tracking\":\"" + (req.outboundTrackingNo() != null ? req.outboundTrackingNo() : "") + "\"}",
                httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/parcels/{id}/deliver")
    public ResponseEntity<ParcelDto> deliverParcel(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        ParcelDto result = parcelService.deliverParcel(id);
        auditLogService.log(currentUser.id(), "PARCEL_DELIVERED", "PARCEL", String.valueOf(id),
                null, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDto> createProduct(
            @Valid @RequestBody CreateProductRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(productService.createProduct(req, currentUser.id()));
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductDto>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(productService.listProducts(pageable));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.ok(productService.updateProduct(id, req));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ── 新货产品管理 ───────────────────────────────────────────────────────────────

    @PostMapping("/new-products")
    public ResponseEntity<ProductDto> createNewProduct(
            @Valid @RequestBody CreateProductRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(newProductService.createProduct(req, currentUser.id()));
    }

    @GetMapping("/new-products")
    public ResponseEntity<Page<ProductDto>> listNewProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(newProductService.listProducts(pageable));
    }

    @PutMapping("/new-products/{id}")
    public ResponseEntity<ProductDto> updateNewProduct(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.ok(newProductService.updateProduct(id, req));
    }

    @DeleteMapping("/new-products/{id}")
    public ResponseEntity<Void> deleteNewProduct(@PathVariable Long id) {
        newProductService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inventory/inbound")
    public ResponseEntity<InventoryDto> inbound(
            @Valid @RequestBody InboundRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(inventoryService.inbound(req, currentUser.id()));
    }

    @GetMapping("/inventory")
    public ResponseEntity<Page<InventoryDto>> listInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        return ResponseEntity.ok(inventoryService.listInventory(pageable));
    }

    @GetMapping("/inventory/transactions")
    public ResponseEntity<Page<InventoryTransactionDto>> listTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(inventoryService.listTransactions(pageable));
    }
}
