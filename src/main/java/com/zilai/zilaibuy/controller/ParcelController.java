package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.parcel.CreateParcelRequest;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.entity.VasRequestEntity;
import com.zilai.zilaibuy.entity.OrderItemEntity;
import com.zilai.zilaibuy.repository.ForwardingParcelRepository;
import com.zilai.zilaibuy.repository.OrderItemRepository;
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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ForwardingParcelService parcelService;
    private final OrderService orderService;
    private final HbrService hbrService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
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

    record ReceiverAddress(String fullName, String phone, String street, String city, String province, String postalCode, String country) {}
    record ShippingRequestBody(List<Long> parcelIds, List<Long> orderItemIds, String shippingLine, String shippingLineName, Integer estimatedFeeJpy, double totalCny, boolean addInspection, boolean addPhoto, ReceiverAddress receiverAddress) {}
    record ShippingRequestResponse(long orderId, String orderNo, String hbrWarning) {}

    @PostMapping("/shipping-request")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ShippingRequestResponse> createShippingRequest(
            @RequestBody ShippingRequestBody req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        // Unified packing: merge proxy orders + forwarding parcels into ONE order via createPackingRequest
        List<com.zilai.zilaibuy.dto.order.OrderDto> results =
                orderService.createPackingRequest(req.orderItemIds(), req.parcelIds(), currentUser);

        // Primary order is the first in the result list
        if (results.isEmpty()) {
            throw new RuntimeException("打包申请失败");
        }
        com.zilai.zilaibuy.dto.order.OrderDto primaryDto = results.get(0);
        OrderEntity primaryOrder = orderRepository.findById(primaryDto.id())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update notes with shipping request info
        int parcelCount = req.parcelIds() != null ? req.parcelIds().size() : 0;
        String notes = String.format("转运申请 | 线路: %s | 验货: %s | 拍照: %s | 包裹数: %d",
                req.shippingLine() != null ? req.shippingLine() : "",
                req.addInspection() ? "是" : "否",
                req.addPhoto() ? "是" : "否",
                parcelCount);
        primaryOrder.setNotes(notes);

        // Persist shipping line, line name, estimated fee and receiver address
        if (req.shippingLine() != null && !req.shippingLine().isBlank()) {
            primaryOrder.setRequestedShippingLine(req.shippingLine());
        }
        if (req.shippingLineName() != null && !req.shippingLineName().isBlank()) {
            primaryOrder.setRequestedShippingLineName(req.shippingLineName());
        }
        if (req.estimatedFeeJpy() != null) {
            primaryOrder.setEstimatedShippingFeeJpy(req.estimatedFeeJpy());
        }
        if (req.receiverAddress() != null) {
            try {
                primaryOrder.setReceiverAddress(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(req.receiverAddress()));
            } catch (Exception ignored) {}
        }

        // 收集所有快递单号（转运包裹 + 代购单商品单号），调用 HBR 创建集运单
        List<String> allTrackingNumbers = new java.util.ArrayList<>();
        if (req.parcelIds() != null && !req.parcelIds().isEmpty()) {
            forwardingParcelRepository.findAllById(req.parcelIds()).stream()
                    .filter(p -> p.getInboundTrackingNo() != null && !p.getInboundTrackingNo().isBlank())
                    .map(ForwardingParcelEntity::getInboundTrackingNo)
                    .forEach(allTrackingNumbers::add);
        }
        if (req.orderItemIds() != null && !req.orderItemIds().isEmpty()) {
            orderItemRepository.findAllById(req.orderItemIds()).stream()
                    .filter(item -> item.getItemTrackingNo() != null && !item.getItemTrackingNo().isBlank())
                    .map(OrderItemEntity::getItemTrackingNo)
                    .forEach(allTrackingNumbers::add);
        }
        log.info("createShippingRequest: trackingNumbers={}, receiverAddress={}", allTrackingNumbers, req.receiverAddress());
        if (!allTrackingNumbers.isEmpty()) {
            java.util.Map<String, String> addrMap = null;
            if (req.receiverAddress() != null) {
                addrMap = new java.util.LinkedHashMap<>();
                ReceiverAddress a = req.receiverAddress();
                if (a.fullName() != null) addrMap.put("fullName", a.fullName());
                if (a.phone() != null) addrMap.put("phone", a.phone());
                if (a.street() != null) addrMap.put("street", a.street());
                if (a.city() != null) addrMap.put("city", a.city());
                if (a.province() != null) addrMap.put("province", a.province());
                if (a.postalCode() != null) addrMap.put("postalCode", a.postalCode());
                if (a.country() != null) addrMap.put("country", a.country());
            }
            com.zilai.zilaibuy.service.HbrService.HbrResult hbrResult = hbrService.createConsolidatedShipment(
                    allTrackingNumbers, req.shippingLine(), primaryOrder.getUser(), addrMap);
            if (hbrResult.success()) {
                primaryOrder.setPackingNo(hbrResult.packingNo());
            }
            orderRepository.save(primaryOrder);
            String warning = hbrResult.success() ? null : hbrResult.errorMessage();
            return ResponseEntity.ok(new ShippingRequestResponse(primaryOrder.getId(), primaryOrder.getOrderNo(), warning));
        } else {
            log.warn("createShippingRequest: no tracking numbers found for order {}, skipping HBR call",
                    primaryOrder.getOrderNo());
        }
        orderRepository.save(primaryOrder);

        return ResponseEntity.ok(new ShippingRequestResponse(primaryOrder.getId(), primaryOrder.getOrderNo(), null));
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

    // ── Custom VAS task ───────────────────────────────────────────────────────

    record CustomVasRequest(String description, Integer budgetJpy) {}

    @PostMapping("/custom-vas-request")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<com.zilai.zilaibuy.dto.VasRequestDto> createCustomVasRequest(
            @RequestBody CustomVasRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new RuntimeException("User not found"));
        VasRequestEntity vasReq = new VasRequestEntity();
        vasReq.setUser(user);
        vasReq.setServices("custom");
        vasReq.setCustomDescription(req.description());
        vasReq.setCustomBudgetJpy(req.budgetJpy());
        vasReq.setCustomImageUrls("[]");
        vasRequestRepository.save(vasReq);
        // Notify admin
        String notifyEmail = appSettingService.get("vas.notify.email", vasAdminEmail);
        String customerName = user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : user.getPhone();
        emailService.sendVasRequestNotification(notifyEmail, customerName,
                user.getPhone() != null ? user.getPhone() : "",
                user.getEmail() != null ? user.getEmail() : "",
                null, req.description() != null ? req.description() : "", "自定义增值任务");
        return ResponseEntity.ok(com.zilai.zilaibuy.dto.VasRequestDto.from(vasReq));
    }

    record CounterOfferRequest(String message, Integer desiredPriceJpy) {}

    /**
     * Custom VAS tasks only: customer rejects the warehouse quote and proposes a counter-offer.
     * Bounces the task from DONE back to PROCESSING so the warehouse can re-quote; the customer
     * then receives a fresh payment request. Standard fixed-price services cannot be negotiated.
     */
    @PostMapping("/custom-vas/{vasId}/counter-offer")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<com.zilai.zilaibuy.dto.VasRequestDto> counterCustomVasQuote(
            @PathVariable Long vasId,
            @RequestBody CounterOfferRequest body,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        VasRequestEntity vasReq = vasRequestRepository.findById(vasId)
                .orElseThrow(() -> new RuntimeException("VAS request not found"));
        if (!vasReq.getUser().getId().equals(currentUser.id())) return ResponseEntity.status(403).build();
        // Only custom tasks awaiting payment (a quote was sent) can be negotiated.
        if (!"custom".equals(vasReq.getServices())
                || vasReq.getStatus() != VasRequestEntity.VasStatus.DONE) {
            return ResponseEntity.status(409).build();
        }

        Integer desired = body.desiredPriceJpy();
        String message = body.message() != null ? body.message().trim() : "";
        StringBuilder note = new StringBuilder();
        if (desired != null) note.append("期望价 ¥").append(desired).append(" JPY");
        if (!message.isEmpty()) {
            if (note.length() > 0) note.append(" — ");
            note.append(message);
        }
        vasReq.setCustomerCounterNote(note.length() > 0 ? note.toString() : "客户希望重新报价");
        vasReq.setStatus(VasRequestEntity.VasStatus.PROCESSING);
        vasRequestRepository.save(vasReq);

        UserEntity user = vasReq.getUser();
        String notifyEmail = appSettingService.get("vas.notify.email", vasAdminEmail);
        String customerName = user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : user.getPhone();
        emailService.sendCustomVasCounterOfferEmail(notifyEmail, customerName,
                vasReq.getCustomDescription(), vasReq.getAdminQuoteJpy(), desired, message);

        return ResponseEntity.ok(com.zilai.zilaibuy.dto.VasRequestDto.from(vasReq));
    }

    @Value("${app.s3.bucket:zilaibuy-media}")
    private String s3Bucket;

    @Value("${app.s3.region:us-east-1}")
    private String s3Region;

    /** Get presigned S3 URL for custom VAS image upload */
    @GetMapping("/custom-vas/{vasId}/image/presign")
    public ResponseEntity<Map<String, String>> presignCustomVasImage(
            @PathVariable Long vasId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String key = "custom-vas/" + currentUser.id() + "/" + vasId + "/" + UUID.randomUUID() + ".jpg";
        try (software.amazon.awssdk.services.s3.presigner.S3Presigner presigner =
                     software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                             .region(software.amazon.awssdk.regions.Region.of(s3Region)).build()) {
            software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presigned =
                    presigner.presignPutObject(
                            software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                                    .signatureDuration(java.time.Duration.ofMinutes(15))
                                    .putObjectRequest(software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                            .bucket(s3Bucket).key(key).contentType("image/jpeg").build())
                                    .build());
            String publicUrl = "https://" + s3Bucket + ".s3." + s3Region + ".amazonaws.com/" + key;
            return ResponseEntity.ok(Map.of("uploadUrl", presigned.url().toString(), "publicUrl", publicUrl));
        }
    }

    /** Save uploaded image URL to custom VAS request */
    @PostMapping("/custom-vas/{vasId}/image")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> saveCustomVasImage(
            @PathVariable Long vasId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) return ResponseEntity.badRequest().build();
        VasRequestEntity vasReq = vasRequestRepository.findById(vasId)
                .orElseThrow(() -> new RuntimeException("VAS request not found"));
        if (!vasReq.getUser().getId().equals(currentUser.id())) return ResponseEntity.status(403).build();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<String> urls = vasReq.getCustomImageUrls() != null && !vasReq.getCustomImageUrls().isBlank()
                    ? mapper.readValue(vasReq.getCustomImageUrls(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {})
                    : new java.util.ArrayList<>();
            urls.add(imageUrl);
            vasReq.setCustomImageUrls(mapper.writeValueAsString(urls));
            vasRequestRepository.save(vasReq);
        } catch (Exception e) {
            log.warn("Failed to save custom VAS image URL", e);
        }
        return ResponseEntity.ok().build();
    }
}
