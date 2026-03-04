package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
}
