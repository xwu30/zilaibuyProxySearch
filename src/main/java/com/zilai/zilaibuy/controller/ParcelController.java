package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.parcel.CreateParcelRequest;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.entity.VasRequestEntity;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.repository.VasRequestRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.EmailService;
import com.zilai.zilaibuy.service.ForwardingParcelService;
import com.zilai.zilaibuy.service.HbrService;
import com.zilai.zilaibuy.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ForwardingParcelService parcelService;
    private final OrderService orderService;
    private final HbrService hbrService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ForwardingParcelRepository forwardingParcelRepository;
    private final VasRequestRepository vasRequestRepository;
    private final EmailService emailService;
    private final com.zilai.zilaibuy.service.AppSettingService appSettingService;

    @Value("${app.admin-email:stellahtor@gmail.com}")
    private String adminEmail;

    @Value("${app.vas-admin-email:rogerxjwu@outlook.com}")
    private String vasAdminEmail;

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

    @GetMapping("/vas-requests")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<com.zilai.zilaibuy.dto.VasRequestDto>> listMyVasRequests(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(
            vasRequestRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id())
                .stream().map(com.zilai.zilaibuy.dto.VasRequestDto::from).toList()
        );
    }

    @GetMapping("/check-tracking")
    public ResponseEntity<Map<String, Boolean>> checkTracking(
            @RequestParam String trackingNo,
            @RequestParam(required = false) Long excludeId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        boolean exists = parcelService.trackingNumberExists(trackingNo.trim(), excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    record ShippingRequestBody(List<Long> parcelIds, List<Long> orderItemIds, String shippingLine, double totalCny, boolean addInspection, boolean addPhoto) {}
    record ShippingRequestResponse(long orderId, String orderNo) {}

    @PostMapping("/shipping-request")
    public ResponseEntity<ShippingRequestResponse> createShippingRequest(
            @RequestBody ShippingRequestBody req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new RuntimeException("User not found"));

        OrderEntity order = new OrderEntity();
        order.setUser(user);
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        order.setOrderNo("HX" + datePart + "-" + randPart);
        order.setTotalCny(BigDecimal.valueOf(req.totalCny()));
        order.setStatus(OrderEntity.OrderStatus.PACKING);
        String notes = String.format("转运申请 | 线路: %s | 验货: %s | 拍照: %s | 包裹数: %d",
                req.shippingLine(),
                req.addInspection() ? "是" : "否",
                req.addPhoto() ? "是" : "否",
                req.parcelIds().size());
        order.setNotes(notes);

        OrderEntity saved = orderRepository.save(order);

        List<ForwardingParcelEntity> parcels = forwardingParcelRepository.findAllById(req.parcelIds());
        for (ForwardingParcelEntity parcel : parcels) {
            if (!parcel.getUser().getId().equals(currentUser.id())) continue;
            parcel.setLinkedOrder(saved);
            parcel.setStatus(ForwardingParcelEntity.ParcelStatus.PACKING);
        }
        forwardingParcelRepository.saveAll(parcels);

        // Advance any selected proxy order items to PACKING status
        if (req.orderItemIds() != null && !req.orderItemIds().isEmpty()) {
            orderService.createPackingRequest(req.orderItemIds(), null, currentUser);
        }

        // Call HBR createconsolidatedshipment and store returned order_Id as packingNo
        if (!parcels.isEmpty() && req.shippingLine() != null && !req.shippingLine().isBlank()) {
            List<String> trackingNumbers = parcels.stream()
                    .filter(p -> p.getInboundTrackingNo() != null && !p.getInboundTrackingNo().isBlank())
                    .map(ForwardingParcelEntity::getInboundTrackingNo)
                    .toList();
            if (!trackingNumbers.isEmpty()) {
                String hbrOrderId = hbrService.createConsolidatedShipment(trackingNumbers, req.shippingLine());
                if (hbrOrderId != null && !hbrOrderId.isBlank()) {
                    saved.setPackingNo(hbrOrderId);
                    orderRepository.save(saved);
                }
            }
        }

        return ResponseEntity.ok(new ShippingRequestResponse(saved.getId(), saved.getOrderNo()));
    }

    record VasRequestBody(List<Long> parcelIds, List<Long> orderIds, List<String> services, String contactInfo) {}

    @PostMapping("/vas-request")
    public ResponseEntity<Map<String, String>> submitVasRequest(
            @RequestBody VasRequestBody req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String serviceLabel = req.services() == null ? "" : String.join("、", req.services().stream().map(s -> switch (s) {
            case "item_inspect" -> "商品验货费 ¥200/件";
            case "photo"        -> "商品拍照费 ¥300/件";
            case "special_pack" -> "特殊商品处理包装费 ¥300/件";
            default             -> s;
        }).toList());

        StringBuilder itemsSb = new StringBuilder();

        // Update parcel notes and collect descriptions
        if (req.parcelIds() != null && !req.parcelIds().isEmpty()) {
            List<ForwardingParcelEntity> parcels = forwardingParcelRepository.findAllById(req.parcelIds());
            for (ForwardingParcelEntity parcel : parcels) {
                if (!parcel.getUser().getId().equals(currentUser.id())) continue;
                String note = "【增值服务申请】" + serviceLabel;
                parcel.setNotes(note);
                itemsSb.append("  [转运包裹] ").append(parcel.getInboundTrackingNo())
                       .append(" - ").append(parcel.getContent()).append("\n");
            }
            forwardingParcelRepository.saveAll(parcels);
        }

        // Collect order descriptions with full item details
        StringBuilder orderDetailSb = new StringBuilder();
        if (req.orderIds() != null && !req.orderIds().isEmpty()) {
            List<OrderEntity> orders = orderRepository.findAllById(req.orderIds());
            for (OrderEntity order : orders) {
                if (!order.getUser().getId().equals(currentUser.id())) continue;
                itemsSb.append("  [采购订单] ").append(order.getOrderNo()).append("\n");
                orderDetailSb.append("[采购订单] ").append(order.getOrderNo()).append("\n");
                if (order.getItems() != null) {
                    for (var item : order.getItems()) {
                        orderDetailSb.append("  · ").append(item.getProductTitle())
                                .append(" x").append(item.getQuantity())
                                .append(" ¥").append(item.getPriceCny()).append(" CNY");
                        if (item.getPlatform() != null && !item.getPlatform().isBlank())
                            orderDetailSb.append(" [").append(item.getPlatform()).append("]");
                        orderDetailSb.append("\n");
                    }
                }
                orderDetailSb.append("  订单合计: ¥").append(order.getTotalCny()).append(" CNY\n\n");
            }
        }

        // Build full detail for email (parcels + orders with items)
        StringBuilder emailDetailSb = new StringBuilder();
        if (!itemsSb.isEmpty()) {
            String parcelSection = itemsSb.toString();
            if (parcelSection.contains("[转运包裹]")) {
                emailDetailSb.append(parcelSection.lines()
                        .filter(l -> l.contains("[转运包裹]"))
                        .collect(java.util.stream.Collectors.joining("\n"))).append("\n\n");
            }
        }
        if (!orderDetailSb.isEmpty()) emailDetailSb.append(orderDetailSb);

        String customerName = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername() : user.getPhone();
        String customerPhone = user.getPhone() != null ? user.getPhone() : "";
        String customerEmail = user.getEmail() != null ? user.getEmail() : "";

        // Persist VAS request to DB
        VasRequestEntity vasReq = new VasRequestEntity();
        vasReq.setUser(user);
        vasReq.setParcelIds(req.parcelIds() == null ? null : req.parcelIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        vasReq.setOrderIds(req.orderIds() == null ? null : req.orderIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        vasReq.setServices(String.join(",", req.services()));
        vasReq.setItemsSummary(itemsSb.toString().trim());
        vasRequestRepository.save(vasReq);

        // Read notification email from DB settings (admin-configurable), fall back to app property
        String notifyEmail = appSettingService.get("vas.notify.email", vasAdminEmail);
        String fullItemsDetail = emailDetailSb.isEmpty() ? itemsSb.toString() : emailDetailSb.toString();
        emailService.sendVasRequestNotification(notifyEmail, customerName, customerPhone,
                customerEmail, req.contactInfo(), fullItemsDetail, serviceLabel);

        return ResponseEntity.ok(Map.of("message", "增值服务申请已提交，仓库将尽快处理"));
    }
}
