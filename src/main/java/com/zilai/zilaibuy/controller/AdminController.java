package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.admin.*;
import com.zilai.zilaibuy.dto.order.OrderDetailDto;
import com.zilai.zilaibuy.dto.order.OrderDto;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.*;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.AuditLogService;
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
    private final AuditLogService auditLogService;
    private final ForwardingParcelService parcelService;
    private final OrderService orderService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailConfirmationRepository emailConfirmationRepository;
    private final PendingEmailRegistrationRepository pendingEmailRegistrationRepository;

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
            @RequestBody AdminUpdateOrderRequest req) {
        return ResponseEntity.ok(orderService.adminUpdateOrder(id, req.status(), req.transitTrackingNo(), req.transitCarrier()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/orders/{id}/service-fee")
    public ResponseEntity<OrderDetailDto> setServiceFee(
            @PathVariable Long id,
            @RequestBody AdminSetServiceFeeRequest req) {
        return ResponseEntity.ok(orderService.setServiceFee(id, req.serviceFeeJpy(), req.serviceFeeMemo()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE', 'SUPPORT')")
    @PutMapping("/orders/{id}/packing-info")
    public ResponseEntity<OrderDetailDto> savePackingInfo(
            @PathVariable Long id,
            @RequestBody AdminSavePackingInfoRequest req) {
        return ResponseEntity.ok(orderService.savePackingInfo(id, req.weightG(), req.lengthCm(), req.widthCm(), req.heightCm(), req.packingPhotoUrl()));
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
            @RequestBody AdminUpdateParcelRequest req) {
        Integer weightGrams = req.weightKg() != null ? (int) Math.round(req.weightKg() * 1000) : null;
        return ResponseEntity.ok(parcelService.adminUpdateParcel(id, req.status(), weightGrams,
                req.outboundTrackingNo(), req.notes(), req.content(), req.inboundTrackingNo(), req.carrier()));
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
                orderRepository.countByStatus(OrderEntity.OrderStatus.PACKING),
                orderRepository.countByStatus(OrderEntity.OrderStatus.AWAITING_PAYMENT)
        ));
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

}
