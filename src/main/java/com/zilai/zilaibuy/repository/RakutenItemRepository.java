package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.RakutenItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RakutenItemRepository extends JpaRepository<RakutenItemEntity, Long> {
    Optional<RakutenItemEntity> findByItemCode(String itemCode);

    // 查询带有关键字的商品，按更新时间排序
    @Query("SELECT r FROM RakutenItemEntity r WHERE r.isActive = true AND (:kw IS NULL OR r.itemName LIKE CONCAT('%', :kw, '%')) ORDER BY r.lastFetchedAt DESC")
    Page<RakutenItemEntity> search(String kw, Pageable pageable);
}
