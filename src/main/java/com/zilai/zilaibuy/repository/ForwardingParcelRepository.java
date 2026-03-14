package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.ForwardingParcelEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
