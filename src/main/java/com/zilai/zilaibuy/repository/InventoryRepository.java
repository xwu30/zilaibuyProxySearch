package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.InventoryEntity;
import com.zilai.zilaibuy.entity.InventoryTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findBySku(String sku);
}
