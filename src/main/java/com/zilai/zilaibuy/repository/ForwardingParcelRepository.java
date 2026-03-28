package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ForwardingParcelRepository extends JpaRepository<ForwardingParcelEntity, Long> {

    List<ForwardingParcelEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ForwardingParcelEntity> findByStatus(ForwardingParcelEntity.ParcelStatus status, Pageable pageable);

    Optional<ForwardingParcelEntity> findByInboundTrackingNo(String inboundTrackingNo);

    List<ForwardingParcelEntity> findByUserIdAndStatus(Long userId, ForwardingParcelEntity.ParcelStatus status);

    List<ForwardingParcelEntity> findByUserIdAndStatusIn(Long userId, List<ForwardingParcelEntity.ParcelStatus> statuses);

    List<ForwardingParcelEntity> findByLinkedOrderId(Long orderId);

    long countByUserId(Long userId);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(inbound_code, '-', -1) AS UNSIGNED)), 0) FROM forwarding_parcels WHERE user_id = :userId AND inbound_code IS NOT NULL", nativeQuery = true)
    long findMaxInboundCodeSeqForUser(@Param("userId") Long userId);

    @Query("SELECT p FROM ForwardingParcelEntity p " +
           "WHERE (:status IS NULL OR p.status = :status) " +
           "AND (:qLike IS NULL OR " +
           "  (p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike) OR " +
           "  (p.outboundTrackingNo IS NOT NULL AND p.outboundTrackingNo LIKE :qLike) OR " +
           "  p.content LIKE :qLike)")
    Page<ForwardingParcelEntity> findByStatusAndSearch(
            @Param("status") ForwardingParcelEntity.ParcelStatus status,
            @Param("qLike") String qLike,
            Pageable pageable);

    @Query(value = "SELECT p FROM ForwardingParcelEntity p " +
           "WHERE (:userId IS NULL OR p.user.id = :userId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:dateFrom IS NULL OR p.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR p.createdAt < :dateTo) " +
           "AND (:qLike IS NULL OR " +
           "  (p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike) OR " +
           "  (p.inboundCode IS NOT NULL AND p.inboundCode LIKE :qLike) OR " +
           "  p.content LIKE :qLike)",
           countQuery = "SELECT COUNT(p) FROM ForwardingParcelEntity p " +
           "WHERE (:userId IS NULL OR p.user.id = :userId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:dateFrom IS NULL OR p.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR p.createdAt < :dateTo) " +
           "AND (:qLike IS NULL OR " +
           "  (p.inboundTrackingNo IS NOT NULL AND p.inboundTrackingNo LIKE :qLike) OR " +
           "  (p.inboundCode IS NOT NULL AND p.inboundCode LIKE :qLike) OR " +
           "  p.content LIKE :qLike)")
    Page<ForwardingParcelEntity> findByFilters(
            @Param("userId") Long userId,
            @Param("status") ForwardingParcelEntity.ParcelStatus status,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo,
            @Param("qLike") String qLike,
            Pageable pageable);
}
