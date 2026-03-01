package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.admin.*;
import com.zilai.zilaibuy.entity.OrderEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.AuditLogService;
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

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(userRepository.findAll(pageable).map(AdminUserDto::from));
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

    @GetMapping("/orders")
    public ResponseEntity<Page<AdminOrderDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        OrderEntity.OrderStatus orderStatus = null;
        if (status != null) {
            try { orderStatus = OrderEntity.OrderStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        return ResponseEntity.ok(orderRepository.findByFilters(userId, orderStatus, pageable).map(AdminOrderDto::from));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(new AdminStatsDto(
                userRepository.count(),
                orderRepository.count(),
                orderRepository.sumTotalRevenue(),
                orderRepository.countByStatus(OrderEntity.OrderStatus.PENDING_PAYMENT),
                orderRepository.countByStatus(OrderEntity.OrderStatus.PURCHASING),
                orderRepository.countByStatus(OrderEntity.OrderStatus.IN_WAREHOUSE),
                orderRepository.countByStatus(OrderEntity.OrderStatus.SHIPPED)
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
