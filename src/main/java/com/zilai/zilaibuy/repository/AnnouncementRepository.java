package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.AnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long> {
    Page<AnnouncementEntity> findAllByOrderByPinnedDescPublishDateDesc(Pageable pageable);
}
