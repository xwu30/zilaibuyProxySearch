package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.order.OrderDto;
import com.zilai.zilaibuy.dto.support.CustomerDto;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.OrderRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
public class SupportController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @GetMapping("/customers")
    public ResponseEntity<Page<CustomerDto>> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CustomerDto> result = userRepository.findAll(pageable).map(user -> {
            long orderCount = orderRepository.countByUserId(user.getId());
            java.time.LocalDateTime lastOrder = orderRepository
                    .findLastOrderTimeByUserId(user.getId()).orElse(null);
            return new CustomerDto(user.getId(), user.getPhone(), user.getRole().name(),
                    orderCount, lastOrder);
        });
        return ResponseEntity.ok(result);
    }

    @GetMapping("/customers/{userId}/orders")
    public ResponseEntity<Page<OrderDto>> getCustomerOrders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderDto> orders = orderRepository.findByUserId(userId, pageable)
                .map(OrderDto::from);
        return ResponseEntity.ok(orders);
    }
}
