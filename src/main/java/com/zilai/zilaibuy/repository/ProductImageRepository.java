package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {
    void deleteByProductId(Long productId);
}
