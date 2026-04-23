package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.VasRequestDto;
import com.zilai.zilaibuy.dto.admin.*;
import com.zilai.zilaibuy.dto.order.OrderDetailDto;
import com.zilai.zilaibuy.dto.order.OrderDto;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.HbrCallbackLogEntity;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.entity.VasRequestEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.*;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.AuditLogService;
import com.zilai.zilaibuy.service.EmailService;
import com.zilai.zilaibuy.service.ForwardingParcelService;
import com.zilai.zilaibuy.service.OrderService;
import java.math.BigDecimal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final HbrCallbackLogRepository hbrCallbackLogRepository;
    private final AuditLogService auditLogService;
    private final ForwardingParcelService parcelService;
    private final OrderService orderService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailConfirmationRepository emailConfirmationRepository;
    private final PendingEmailRegistrationRepository pendingEmailRegistrationRepository;
    private final VasRequestRepository vasRequestRepository;
    private final EmailService emailService;
    private final com.zilai.zilaibuy.service.AppSettingService appSettingService;

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(userRepository.findAll(pageable).map(AdminUserDto::from));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserDto> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (req.username() != null) {
            if (StringUtils.hasText(req.username()) && !req.username().equals(user.getUsername())) {
                if (userRepository.existsByUsername(req.username())) {
                    throw new AppException(HttpStatus.CONFLICT, "用户名已被使用");
                }
            }
            user.setUsername(StringUtils.hasText(req.username()) ? req.username() : null);
        }
        if (req.email() != null) {
            if (StringUtils.hasText(req.email()) && !req.email().equals(user.getEmail())) {
                if (userRepository.findByEmail(req.email()).filter(u -> !u.getId().equals(id)).isPresent()) {
                    throw new AppException(HttpStatus.CONFLICT, "邮箱已被其他账户使用");
                }
            }
            user.setEmail(StringUtils.hasText(req.email()) ? req.email() : null);
        }
        if (req.phone() != null && StringUtils.hasText(req.phone()) && !req.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(req.phone())) {
                throw new AppException(HttpStatus.CONFLICT, "手机号已被其他账户使用");
            }
            user.setPhone(req.phone());
        }
        if (req.displayName() != null)      user.setDisplayName(StringUtils.hasText(req.displayName()) ? req.displayName() : null);
        if (req.cloudId() != null) {
            String newCloudId = StringUtils.hasText(req.cloudId()) ? req.cloudId().trim() : null;
            if (newCloudId != null && !newCloudId.equals(user.getCloudId()) && userRepository.existsByCloudId(newCloudId)) {
                throw new AppException(HttpStatus.CONFLICT, "Cloud ID 已被其他账户使用");
            }
            user.setCloudId(newCloudId);
        }
        if (req.shippingFullName() != null)  user.setShippingFullName(req.shippingFullName());
        if (req.shippingPhone() != null)     user.setShippingPhone(req.shippingPhone());
        if (req.shippingStreet() != null)    user.setShippingStreet(req.shippingStreet());
        if (req.shippingCity() != null)      user.setShippingCity(req.shippingCity());
        if (req.shippingProvince() != null)  user.setShippingProvince(req.shippingProvince());
        if (req.shippingPostalCode() != null) user.setShippingPostalCode(req.shippingPostalCode());
        if (req.points() != null)            user.setPoints(Math.max(0, req.points()));

        userRepository.save(user);
        auditLogService.log(currentUser.id(), "USER_UPDATED", "USER", String.valueOf(id), null, httpReq.getRemoteAddr());
        return ResponseEntity.ok(AdminUserDto.from(user));
    }

    @PutMapping("/users/{id}/cloud-id")
    public ResponseEntity<AdminUserDto> setCloudId(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        String newCloudId = body.getOrDefault("cloudId", "").trim();
        if (newCloudId.isEmpty()) newCloudId = null;
        if (newCloudId != null && !newCloudId.equals(user.getCloudId()) && userRepository.existsByCloudId(newCloudId)) {
            throw new AppException(HttpStatus.CONFLICT, "Cloud ID 已被其他账户使用");
        }
        user.setCloudId(newCloudId);
        userRepository.save(user);
        auditLogService.log(currentUser.id(), "USER_CLOUD_ID_SET", "USER", String.valueOf(id), null, httpReq.getRemoteAddr());
        return ResponseEntity.ok(AdminUserDto.from(user));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<AdminUserDto> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        String oldRole = user.getRole().name();
        user.setRole(req.role());
        userRepository.save(user);

        auditLogService.log(currentUser.id(), "USER_ROLE_CHANGED", "USER",
                String.valueOf(id),
                "{\"from\":\"" + oldRole + "\",\"to\":\"" + req.role().name() + "\"}",
                httpReq.getRemoteAddr());

        return ResponseEntity.ok(AdminUserDto.from(user));
    }

    @PutMapping("/users/{id}/lock")
    public ResponseEntity<AdminUserDto> lockUser(
            @PathVariable Long id,
            @RequestBody LockUserRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setLocked(req.locked());
        user.setLockUntil(req.locked() ? LocalDateTime.now().plusHours(24) : null);
        userRepository.save(user);

        auditLogService.log(currentUser.id(), req.locked() ? "USER_LOCKED" : "USER_UNLOCKED",
                "USER", String.valueOf(id), null, httpReq.getRemoteAddr());

        return ResponseEntity.ok(AdminUserDto.from(user));
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        if (id.equals(currentUser.id())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "不能删除自己的账户");
        }
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getRole() == UserEntity.Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "不能删除管理员账户");
        }
        if (orderRepository.countByUserId(id) > 0) {
            throw new AppException(HttpStatus.CONFLICT, "用户有订单记录，无法删除。如需封禁请使用锁定功能");
        }

        // Cascade delete auth data
        refreshTokenRepository.deleteByUserId(id);
        passwordResetTokenRepository.deleteByUser(user);
        emailConfirmationRepository.deleteByUser(user);
        if (user.getEmail() != null) {
            pendingEmailRegistrationRepository.deleteByEmail(user.getEmail());
        }
        userRepository.delete(user);

        auditLogService.log(currentUser.id(), "USER_DELETED", "USER", String.valueOf(id),
                "{\"phone\":\"" + user.getPhone() + "\"}", httpReq.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private static final java.util.List<OrderEntity.OrderStatus> PROXY_STATUSES = java.util.List.of(
            OrderEntity.OrderStatus.PENDING_PAYMENT, OrderEntity.OrderStatus.FEE_QUOTED,
            OrderEntity.OrderStatus.PURCHASING, OrderEntity.OrderStatus.IN_WAREHOUSE);
    private static final java.util.List<OrderEntity.OrderStatus> CONSOLIDATED_STATUSES = java.util.List.of(
            OrderEntity.OrderStatus.PACKING, OrderEntity.OrderStatus.AWAITING_PAYMENT,
            OrderEntity.OrderStatus.SHIPPED, OrderEntity.OrderStatus.DELIVERED);

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @GetMapping("/orders")
    public ResponseEntity<Page<AdminOrderDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statusGroup,
            @RequestParam(required = false) String q) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        OrderEntity.OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            try { orderStatus = OrderEntity.OrderStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        String qLike = (q != null && !q.isBlank()) ? "%" + q.trim() + "%" : null;
        // consolidated tab: always query HX/SH prefix orders; optionally filter by status and search
        if ("consolidated".equals(statusGroup)) {
            // Virtual status SHIPPING_PAID: orders with shippingRoute set (paid, awaiting shipment)
            if ("SHIPPING_PAID".equals(status)) {
                return ResponseEntity.ok(orderRepository.findConsolidatedPaidOrders(userId, qLike, pageable).map(AdminOrderDto::from));
            }
            return ResponseEntity.ok(orderRepository.findConsolidatedOrders(userId, orderStatus, qLike, pageable).map(AdminOrderDto::from));
        }
        // If a specific status is provided (proxy context), use single-status filter
        if (orderStatus != null) {
            return ResponseEntity.ok(orderRepository.findByFilters(userId, orderStatus, null, null, null, pageable).map(AdminOrderDto::from));
        }
        // proxy group
        if ("proxy".equals(statusGroup)) {
            return ResponseEntity.ok(orderRepository.findByFiltersWithStatusIn(userId, PROXY_STATUSES, pageable).map(AdminOrderDto::from));
        }
        return ResponseEntity.ok(orderRepository.findByFilters(userId, null, null, null, null, pageable).map(AdminOrderDto::from));
    }

    record AdminUpdateOrderRequest(OrderEntity.OrderStatus status, String transitTrackingNo, String transitCarrier) {}
    record AdminUpdateOrderItemRequest(int quantity, BigDecimal priceCny) {}
    record AdminSavePackingInfoRequest(Integer weightG, Integer lengthCm, Integer widthCm, Integer heightCm, String packingPhotoUrl) {}
    record AdminSetServiceFeeRequest(Integer serviceFeeJpy, String serviceFeeMemo) {}
    record AdminUpdateParcelRequest(
            com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus status,
            Double weightKg, String outboundTrackingNo, String notes,
            String content, String inboundTrackingNo, String carrier) {}

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/orders/{id}")
    public ResponseEntity<OrderDetailDto> adminUpdateOrder(
            @PathVariable Long id,
            @RequestBody AdminUpdateOrderRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderDetailDto result = orderService.adminUpdateOrder(id, req.status(), req.transitTrackingNo(), req.transitCarrier());
        String detail = req.status() != null ? "{\"status\":\"" + req.status().name() + "\""
                + (req.transitTrackingNo() != null ? ",\"tracking\":\"" + req.transitTrackingNo() + "\"" : "")
                + "}" : null;
        auditLogService.log(currentUser.id(), "ORDER_STATUS_CHANGED", "ORDER", String.valueOf(id),
                detail, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/orders/{id}/service-fee")
    public ResponseEntity<OrderDetailDto> setServiceFee(
            @PathVariable Long id,
            @RequestBody AdminSetServiceFeeRequest req) {
        return ResponseEntity.ok(orderService.setServiceFee(id, req.serviceFeeJpy(), req.serviceFeeMemo()));
    }

    record AdminSaveShippingQuoteRequest(String quotedRoute, Integer quotedFeeJpy) {}

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE', 'SUPPORT')")
    @PutMapping("/orders/{id}/shipping-quote")
    public ResponseEntity<OrderDetailDto> saveShippingQuote(
            @PathVariable Long id,
            @RequestBody AdminSaveShippingQuoteRequest req) {
        return ResponseEntity.ok(orderService.saveShippingQuote(id, req.quotedRoute(), req.quotedFeeJpy()));
    }

    record AdminSavePhotoRequest(String packingPhotoUrl) {}

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE', 'SUPPORT')")
    @PutMapping("/orders/{id}/packing-photo")
    public ResponseEntity<OrderDetailDto> savePackingPhoto(
            @PathVariable Long id,
            @RequestBody AdminSavePhotoRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderDetailDto result = orderService.savePackingPhotoOnly(id, req.packingPhotoUrl());
        auditLogService.log(currentUser.id(), "PACKING_PHOTO_UPLOADED", "ORDER",
                String.valueOf(id), null, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE', 'SUPPORT')")
    @PutMapping("/orders/{id}/packing-info")
    public ResponseEntity<OrderDetailDto> savePackingInfo(
            @PathVariable Long id,
            @RequestBody AdminSavePackingInfoRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderDetailDto result = orderService.savePackingInfo(id, req.weightG(), req.lengthCm(), req.widthCm(), req.heightCm(), req.packingPhotoUrl());
        String detail = "{\"weightG\":" + req.weightG() + ",\"size\":\"" + req.lengthCm() + "x" + req.widthCm() + "x" + req.heightCm() + "\"}";
        auditLogService.log(currentUser.id(), "ORDER_PACKING_INFO_SAVED", "ORDER", String.valueOf(id),
                detail, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE', 'SUPPORT')")
    @PostMapping("/orders/{id}/packing-complete")
    public ResponseEntity<OrderDetailDto> completePackingAndNotify(
            @PathVariable Long id,
            @RequestBody AdminSavePackingInfoRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        OrderDetailDto result = orderService.savePackingInfo(id, req.weightG(), req.lengthCm(), req.widthCm(), req.heightCm(), req.packingPhotoUrl());
        auditLogService.log(currentUser.id(), "ORDER_PACKING_COMPLETE", "ORDER", String.valueOf(id),
                "{\"weightG\":" + req.weightG() + "}", httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/orders/{orderId}/items/{itemId}")
    public ResponseEntity<OrderDto> adminUpdateOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody AdminUpdateOrderItemRequest req) {
        return ResponseEntity.ok(orderService.adminUpdateOrderItem(orderId, itemId, req.quantity(), req.priceCny()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/parcels/{id}")
    public ResponseEntity<ParcelDto> adminUpdateParcel(
            @PathVariable Long id,
            @RequestBody AdminUpdateParcelRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpReq) {
        ParcelDto result = parcelService.adminUpdateParcel(id, req.status(), req.weightKg(),
                req.outboundTrackingNo(), req.notes(), req.content(), req.inboundTrackingNo(), req.carrier());
        String detail = req.status() != null ? "{\"status\":\"" + req.status().name() + "\"}" : null;
        auditLogService.log(currentUser.id(), "PARCEL_UPDATED", "PARCEL", String.valueOf(id),
                detail, httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @GetMapping("/parcels")
    public ResponseEntity<Page<ParcelDto>> listParcels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus parcelStatus = null;
        if (status != null && !status.isBlank()) {
            try { parcelStatus = com.zilai.zilaibuy.entity.ForwardingParcelEntity.ParcelStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        String qLike = (q != null && !q.isBlank()) ? "%" + q.trim() + "%" : null;
        return ResponseEntity.ok(parcelService.findByFilters(userId, parcelStatus, dateFrom, dateTo, qLike, pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(new AdminStatsDto(
                userRepository.count(),
                orderRepository.count(),
                orderRepository.sumTotalRevenue(),
                orderRepository.countByStatus(OrderEntity.OrderStatus.PENDING_PAYMENT),
                orderRepository.countByStatus(OrderEntity.OrderStatus.FEE_QUOTED),
                orderRepository.countByStatus(OrderEntity.OrderStatus.PURCHASING),
                orderRepository.countByStatus(OrderEntity.OrderStatus.IN_WAREHOUSE),
                orderRepository.countByStatus(OrderEntity.OrderStatus.SHIPPED),
                orderRepository.countConsolidatedByStatus(OrderEntity.OrderStatus.PACKING),
                orderRepository.countConsolidatedByStatus(OrderEntity.OrderStatus.AWAITING_PAYMENT),
                orderRepository.countConsolidatedShippingPaid()
        ));
    }

    @GetMapping("/hbr-callback-logs")
    public ResponseEntity<Page<HbrCallbackLogEntity>> listHbrCallbackLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String type) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<HbrCallbackLogEntity> result = (type != null && !type.isBlank())
                ? hbrCallbackLogRepository.findByCallbackTypeOrderByCreatedAtDesc(type, pageable)
                : hbrCallbackLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/vas-requests")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<VasRequestDto>> listVasRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(vasRequestRepository.findAllByOrderByCreatedAtDesc(pageable).map(VasRequestDto::from));
    }

    record VasStatusUpdate(String status, String adminNotes, String serviceResults) {}

    @PatchMapping("/vas-requests/{id}")
    @Transactional
    public ResponseEntity<VasRequestDto> updateVasRequest(
            @PathVariable Long id,
            @RequestBody VasStatusUpdate body) {
        VasRequestEntity req = vasRequestRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "申请不存在"));
        boolean wasNotDone = req.getStatus() != VasRequestEntity.VasStatus.DONE;
        if (body.status() != null) req.setStatus(VasRequestEntity.VasStatus.valueOf(body.status()));
        if (body.adminNotes() != null) req.setAdminNotes(body.adminNotes());
        if (body.serviceResults() != null) req.setServiceResults(body.serviceResults());
        vasRequestRepository.save(req);

        // Send email when transitioning to DONE
        if (wasNotDone && req.getStatus() == VasRequestEntity.VasStatus.DONE) {
            UserEntity user = req.getUser();
            String toEmail = user.getEmail();
            String displayName = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername() : user.getPhone();
            String serviceLabel = req.getServices() == null ? "" :
                    String.join("、", java.util.Arrays.stream(req.getServices().split(",")).map(s -> switch (s.trim()) {
                        case "item_inspect" -> "商品验货费";
                        case "photo"        -> "商品拍照费";
                        case "special_pack" -> "特殊商品处理包装费";
                        default             -> s;
                    }).toList());
            emailService.sendVasCompletionEmail(toEmail, displayName, serviceLabel, req.getItemsSummary(), req.getAdminNotes());
        }

        return ResponseEntity.ok(VasRequestDto.from(req));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogService.AuditLogDto>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(auditLogService.listLogs(action, operatorId, from, to, pageable));
    }

    // ── App Settings ──────────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<java.util.Map<String, String>> getSettings() {
        return ResponseEntity.ok(appSettingService.getAll());
    }

    record UpdateSettingRequest(String key, String value) {}

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSetting(@RequestBody UpdateSettingRequest req) {
        if (req.key() == null || req.key().isBlank()) return ResponseEntity.badRequest().build();
        appSettingService.set(req.key(), req.value() != null ? req.value() : "");
        return ResponseEntity.ok().build();
    }

}
