package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.dto.warehouse.*;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.ForwardingParcelService;
import com.zilai.zilaibuy.service.InventoryService;
import com.zilai.zilaibuy.service.OrderService;
import com.zilai.zilaibuy.service.ProductService;
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
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final ForwardingParcelService parcelService;

    record CheckinRequest(String orderNo) {}

    @PostMapping("/checkin")
    public ResponseEntity<OrderService.CheckinResult> checkin(@RequestBody CheckinRequest req) {
        OrderService.CheckinResult orderResult = orderService.checkinByOrderNo(req.orderNo());
        if (orderResult.success() || !orderResult.message().equals("未找到匹配订单")) {
            return ResponseEntity.ok(orderResult);
        }
        // Fall back to forwarding parcel
        OrderService.CheckinResult parcelResult = parcelService.checkinByTrackingNo(req.orderNo());
        return ResponseEntity.ok(parcelResult != null ? parcelResult : orderResult);
    }

    @PostMapping("/checkin/batch")
    public ResponseEntity<java.util.List<OrderService.CheckinResult>> checkinBatch(
            @RequestBody java.util.List<String> orderNos) {
        return ResponseEntity.ok(orderNos.stream()
                .map(no -> {
                    OrderService.CheckinResult r = orderService.checkinByOrderNo(no);
                    if (r.success() || !r.message().equals("未找到匹配订单")) return r;
                    OrderService.CheckinResult pr = parcelService.checkinByTrackingNo(no);
                    return pr != null ? pr : r;
                })
                .toList());
    }

    @GetMapping("/parcels")
    public ResponseEntity<Page<ParcelDto>> listParcels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null && !status.isBlank()) {
            var s = com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus.valueOf(status);
            return ResponseEntity.ok(parcelService.listAllParcelsByStatus(s, pageable));
        }
        return ResponseEntity.ok(parcelService.listAllParcels(pageable));
    }

    record ShipParcelRequest(String outboundTrackingNo, String notes) {}

    @PutMapping("/parcels/{id}/ship")
    public ResponseEntity<ParcelDto> shipParcel(
            @PathVariable Long id,
            @RequestBody ShipParcelRequest req) {
        return ResponseEntity.ok(parcelService.shipParcel(id, req.outboundTrackingNo(), req.notes()));
    }

    @PutMapping("/parcels/{id}/deliver")
    public ResponseEntity<ParcelDto> deliverParcel(@PathVariable Long id) {
        return ResponseEntity.ok(parcelService.deliverParcel(id));
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
