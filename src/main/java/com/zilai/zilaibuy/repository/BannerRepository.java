package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.BannerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<BannerEntity, Long> {
    List<BannerEntity> findByEnabledTrueOrderBySortOrderAsc();
    List<BannerEntity> findAllByOrderBySortOrderAsc();
}
