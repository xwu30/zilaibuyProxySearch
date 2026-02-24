package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.entity.AuditLogEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.AuditLogRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional
    public void log(Long operatorId, String action, String resourceType,
                    String resourceId, String detail, String ipAddress) {
        try {
            AuditLogEntity entry = new AuditLogEntity();
            if (operatorId != null) {
                userRepository.findById(operatorId).ifPresent(entry::setOperator);
            }
            entry.setAction(action);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            entry.setDetail(detail);
            entry.setIpAddress(ipAddress);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> listLogs(String action, Long operatorId,
                                       LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditLogRepository.findByFilters(action, operatorId, from, to, pageable)
                .map(AuditLogDto::from);
    }

    public record AuditLogDto(
            Long id,
            Long operatorId,
            String action,
            String resourceType,
            String resourceId,
            String detail,
            String ipAddress,
            LocalDateTime createdAt
    ) {
        public static AuditLogDto from(AuditLogEntity e) {
            Long opId = e.getOperator() != null ? e.getOperator().getId() : null;
            return new AuditLogDto(e.getId(), opId, e.getAction(), e.getResourceType(),
                    e.getResourceId(), e.getDetail(), e.getIpAddress(), e.getCreatedAt());
        }
    }
}
