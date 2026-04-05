package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    Optional<OrderItemEntity> findByItemTrackingNo(String itemTrackingNo);

    @Query("SELECT COUNT(i) FROM OrderItemEntity i WHERE i.order.id = :orderId")
    long countByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COUNT(i) FROM OrderItemEntity i WHERE i.order.id = :orderId AND i.itemStatus = :status")
    long countByOrderIdAndItemStatus(@Param("orderId") Long orderId, @Param("status") String status);

    @Query("SELECT COUNT(i) FROM OrderItemEntity i WHERE i.order.id = :orderId AND i.itemTrackingNo IS NOT NULL AND i.itemTrackingNo <> ''")
    long countByOrderIdAndItemTrackingNoIsNotNull(@Param("orderId") Long orderId);

    @Modifying
    @Query("UPDATE OrderItemEntity i SET i.order.id = :newOrderId WHERE i.order.id = :oldOrderId")
    void moveItemsToOrder(@Param("oldOrderId") Long oldOrderId, @Param("newOrderId") Long newOrderId);
}
