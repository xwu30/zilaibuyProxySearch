package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.NewProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewProductRepository extends JpaRepository<NewProductEntity, Long> {
    Page<NewProductEntity> findAll(Pageable pageable);
    Page<NewProductEntity> findByPublishedTrue(Pageable pageable);
}
