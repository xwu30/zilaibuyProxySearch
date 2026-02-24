package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.InventoryTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransactionEntity, Long> {
    Page<InventoryTransactionEntity> findByInventoryId(Long inventoryId, Pageable pageable);
}
