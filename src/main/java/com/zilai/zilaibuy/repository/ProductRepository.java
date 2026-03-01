package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Page<ProductEntity> findAll(Pageable pageable);
    Page<ProductEntity> findByPublishedTrue(Pageable pageable);
}
