package com.zilai.zilaibuy.repository;

import com.zilai.zilaibuy.entity.ContactLeadEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactLeadRepository extends JpaRepository<ContactLeadEntity, Long> {
    Page<ContactLeadEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
