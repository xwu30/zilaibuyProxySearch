package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.EmailConfirmationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailConfirmationRepository extends JpaRepository<EmailConfirmationEntity, Long> {
    Optional<EmailConfirmationEntity> findByToken(String token);
}
