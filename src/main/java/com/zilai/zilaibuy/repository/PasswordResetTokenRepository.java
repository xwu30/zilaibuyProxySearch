package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.PasswordResetTokenEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.user = :user")
    void deleteByUser(@Param("user") UserEntity user);
}
