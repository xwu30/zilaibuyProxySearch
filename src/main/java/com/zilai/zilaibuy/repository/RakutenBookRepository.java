package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.RakutenBookEntity;
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

public interface RakutenBookRepository extends JpaRepository<RakutenBookEntity, Long> {

    Optional<RakutenBookEntity> findByIsbn(String isbn);

    List<RakutenBookEntity> findByIsbnIn(Collection<String> isbns);

    @Transactional
    @Modifying
    @Query("UPDATE RakutenBookEntity b SET b.isActive = false WHERE b.lastFetchedAt < :threshold AND b.isActive = true")
    int deactivateStaleItems(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT b FROM RakutenBookEntity b WHERE b.isActive = true AND (:kw IS NULL OR LOWER(b.keyword) LIKE CONCAT('%', LOWER(:kw), '%') OR b.title LIKE CONCAT('%', :kw, '%') OR b.titleZh LIKE CONCAT('%', :kw, '%') OR b.author LIKE CONCAT('%', :kw, '%')) ORDER BY b.lastFetchedAt DESC")
    Page<RakutenBookEntity> search(String kw, Pageable pageable);

    @Query(value = "SELECT * FROM rakuten_book WHERE is_active = 1 ORDER BY RAND(TO_DAYS(NOW()))",
           countQuery = "SELECT COUNT(*) FROM rakuten_book WHERE is_active = 1",
           nativeQuery = true)
    Page<RakutenBookEntity> searchRandom(Pageable pageable);
}
