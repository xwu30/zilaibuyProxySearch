package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;


public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

    Page<OrderEntity> findByUserIdAndStatus(Long userId, OrderEntity.OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByStatus(OrderEntity.OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND (:status IS NULL OR o.status = :status)")
    Page<OrderEntity> findByFilters(
            @Param("userId") Long userId,
            @Param("status") OrderEntity.OrderStatus status,
            Pageable pageable);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT MAX(o.createdAt) FROM OrderEntity o WHERE o.user.id = :userId")
    Optional<LocalDateTime> findLastOrderTimeByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.orderNo LIKE :prefix%")
    long countByOrderNoPrefix(@Param("prefix") String prefix);
}
