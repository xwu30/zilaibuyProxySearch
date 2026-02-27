package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.PendingEmailRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingEmailRegistrationRepository extends JpaRepository<PendingEmailRegistrationEntity, String> {
    Optional<PendingEmailRegistrationEntity> findByToken(String token);
    void deleteByEmail(String email);
}
