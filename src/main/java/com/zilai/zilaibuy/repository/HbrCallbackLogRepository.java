package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.HbrCallbackLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HbrCallbackLogRepository extends JpaRepository<HbrCallbackLogEntity, Long> {

    Page<HbrCallbackLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<HbrCallbackLogEntity> findByCallbackTypeOrderByCreatedAtDesc(String callbackType, Pageable pageable);
}
