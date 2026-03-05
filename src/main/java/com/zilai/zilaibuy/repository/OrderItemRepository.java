package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    Optional<OrderItemEntity> findByItemTrackingNo(String itemTrackingNo);
}
