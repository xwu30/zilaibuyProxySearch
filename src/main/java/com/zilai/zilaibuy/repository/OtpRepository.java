package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.OtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpEntity, Long> {

    @Query("SELECT COUNT(o) FROM OtpEntity o WHERE o.phone = :phone AND o.createdAt > :since")
    long countByPhoneAndCreatedAtAfter(@Param("phone") String phone, @Param("since") LocalDateTime since);

    @Query("SELECT o FROM OtpEntity o WHERE o.phone = :phone AND o.purpose = :purpose " +
           "AND o.used = false AND o.expiresAt > :now ORDER BY o.createdAt DESC")
    Optional<OtpEntity> findLatestValid(
            @Param("phone") String phone,
            @Param("purpose") OtpEntity.Purpose purpose,
            @Param("now") LocalDateTime now);
}
