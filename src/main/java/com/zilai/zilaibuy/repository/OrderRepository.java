package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

    Page<OrderEntity> findByUserIdAndStatus(Long userId, OrderEntity.OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByStatus(OrderEntity.OrderStatus status, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:dateFrom IS NULL OR o.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR o.createdAt < :dateTo) " +
           "AND o.orderNo NOT LIKE 'HX%' AND o.orderNo NOT LIKE 'SH-%' AND o.orderNo NOT LIKE 'DG-%' " +
           "AND (:qLike IS NULL OR " +
           "  o.orderNo LIKE :qLike OR " +
           "  (o.transitTrackingNo IS NOT NULL AND o.transitTrackingNo LIKE :qLike) OR " +
           "  EXISTS (SELECT i FROM OrderItemEntity i WHERE i.order = o AND i.itemTrackingNo IS NOT NULL AND i.itemTrackingNo LIKE :qLike) OR " +
           "  EXISTS (SELECT p FROM ForwardingParcelEntity p WHERE p.linkedOrder = o AND p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike))",
           countQuery = "SELECT COUNT(DISTINCT o) FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:dateFrom IS NULL OR o.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR o.createdAt < :dateTo) " +
           "AND o.orderNo NOT LIKE 'HX%' AND o.orderNo NOT LIKE 'SH-%' AND o.orderNo NOT LIKE 'DG-%' " +
           "AND (:qLike IS NULL OR " +
           "  o.orderNo LIKE :qLike OR " +
           "  (o.transitTrackingNo IS NOT NULL AND o.transitTrackingNo LIKE :qLike) OR " +
           "  EXISTS (SELECT i FROM OrderItemEntity i WHERE i.order = o AND i.itemTrackingNo IS NOT NULL AND i.itemTrackingNo LIKE :qLike) OR " +
           "  EXISTS (SELECT p FROM ForwardingParcelEntity p WHERE p.linkedOrder = o AND p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike))")
    Page<OrderEntity> findByFilters(
            @Param("userId") Long userId,
            @Param("status") OrderEntity.OrderStatus status,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo,
            @Param("qLike") String qLike,
            Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND o.status IN :statuses " +
           "AND o.orderNo NOT LIKE 'HX%' AND o.orderNo NOT LIKE 'SH-%' AND o.orderNo NOT LIKE 'DG-%'",
           countQuery = "SELECT COUNT(DISTINCT o) FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND o.status IN :statuses " +
           "AND o.orderNo NOT LIKE 'HX%' AND o.orderNo NOT LIKE 'SH-%' AND o.orderNo NOT LIKE 'DG-%'")
    Page<OrderEntity> findByFiltersWithStatusIn(
            @Param("userId") Long userId,
            @Param("statuses") List<OrderEntity.OrderStatus> statuses,
            Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:qLike IS NOT NULL OR o.orderNo LIKE 'HX%' OR o.orderNo LIKE 'SH-%' OR o.orderNo LIKE 'DG-%') " +
           "AND (:qLike IS NULL OR o.orderNo LIKE :qLike OR " +
           "  EXISTS (SELECT p FROM ForwardingParcelEntity p WHERE p.linkedOrder = o AND p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike))",
           countQuery = "SELECT COUNT(DISTINCT o) FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:qLike IS NOT NULL OR o.orderNo LIKE 'HX%' OR o.orderNo LIKE 'SH-%' OR o.orderNo LIKE 'DG-%') " +
           "AND (:qLike IS NULL OR o.orderNo LIKE :qLike OR " +
           "  EXISTS (SELECT p FROM ForwardingParcelEntity p WHERE p.linkedOrder = o AND p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike))")
    Page<OrderEntity> findConsolidatedOrders(
            @Param("userId") Long userId,
            @Param("status") OrderEntity.OrderStatus status,
            @Param("qLike") String qLike,
            Pageable pageable);

    // Shipping paid: PACKING or PURCHASING with shippingRoute set (includes webhook-bug recovery orders)
    @Query(value = "SELECT DISTINCT o FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND o.shippingRoute IS NOT NULL " +
           "AND o.status IN (com.zilai.zilaibuy.entity.OrderEntity.OrderStatus.PACKING, " +
           "                 com.zilai.zilaibuy.entity.OrderEntity.OrderStatus.PURCHASING) " +
           "AND (o.orderNo LIKE 'HX%' OR o.orderNo LIKE 'SH-%' OR o.orderNo LIKE 'DG-%') " +
           "AND (:qLike IS NULL OR o.orderNo LIKE :qLike OR " +
           "  EXISTS (SELECT p FROM ForwardingParcelEntity p WHERE p.linkedOrder = o AND p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike))",
           countQuery = "SELECT COUNT(DISTINCT o) FROM OrderEntity o " +
           "WHERE (:userId IS NULL OR o.user.id = :userId) " +
           "AND o.shippingRoute IS NOT NULL " +
           "AND o.status IN (com.zilai.zilaibuy.entity.OrderEntity.OrderStatus.PACKING, " +
           "                 com.zilai.zilaibuy.entity.OrderEntity.OrderStatus.PURCHASING) " +
           "AND (o.orderNo LIKE 'HX%' OR o.orderNo LIKE 'SH-%' OR o.orderNo LIKE 'DG-%') " +
           "AND (:qLike IS NULL OR o.orderNo LIKE :qLike OR " +
           "  EXISTS (SELECT p FROM ForwardingParcelEntity p WHERE p.linkedOrder = o AND p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike))")
    Page<OrderEntity> findConsolidatedPaidOrders(
            @Param("userId") Long userId,
            @Param("qLike") String qLike,
            Pageable pageable);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT MAX(o.createdAt) FROM OrderEntity o WHERE o.user.id = :userId")
    Optional<LocalDateTime> findLastOrderTimeByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.orderNo LIKE :prefix%")
    long countByOrderNoPrefix(@Param("prefix") String prefix);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.packingNo LIKE :prefix%")
    long countByPackingNoPrefix(@Param("prefix") String prefix);

    @Query("SELECT COALESCE(SUM(o.totalCny), 0) FROM OrderEntity o")
    java.math.BigDecimal sumTotalRevenue();

    long countByStatus(OrderEntity.OrderStatus status);

    Optional<OrderEntity> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<OrderEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderEntity.OrderStatus status);

    Optional<OrderEntity> findByOrderNo(String orderNo);

    List<OrderEntity> findByUserIdAndStatusOrderByCreatedAtAsc(Long userId, OrderEntity.OrderStatus status);
}
