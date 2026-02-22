package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.RakutenItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RakutenItemRepository extends JpaRepository<RakutenItemEntity, Long> {
    Optional<RakutenItemEntity> findByItemCode(String itemCode);

    List<RakutenItemEntity> findByItemCodeIn(Collection<String> itemCodes);

    @Transactional
    @Modifying
    @Query("UPDATE RakutenItemEntity r SET r.isActive = false WHERE r.lastFetchedAt < :threshold AND r.isActive = true")
    int deactivateStaleItems(@Param("threshold") LocalDateTime threshold);

    // 查询带有关键字的商品，按更新时间排序
    @Query("SELECT r FROM RakutenItemEntity r WHERE r.isActive = true AND (:kw IS NULL OR LOWER(r.keyword) LIKE CONCAT('%', LOWER(:kw), '%') OR r.itemName LIKE CONCAT('%', :kw, '%') OR r.itemNameZh LIKE CONCAT('%', :kw, '%')) ORDER BY r.lastFetchedAt DESC")
    Page<RakutenItemEntity> search(String kw, Pageable pageable);
}
