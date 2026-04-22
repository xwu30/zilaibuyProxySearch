package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.VasRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VasRequestRepository extends JpaRepository<VasRequestEntity, Long> {
    Page<VasRequestEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<VasRequestEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<VasRequestEntity> findByStripePaymentIntentId(String stripePaymentIntentId);
}
