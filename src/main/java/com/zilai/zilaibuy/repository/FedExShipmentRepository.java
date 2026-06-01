package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.FedExShipmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FedExShipmentRepository extends JpaRepository<FedExShipmentEntity, Long> {
    Page<FedExShipmentEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
