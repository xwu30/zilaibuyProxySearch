package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByPhone(String phone);
    boolean existsByPhone(String phone);
    Optional<UserEntity> findByEmail(String email);
    boolean existsByUsername(String username);
}
