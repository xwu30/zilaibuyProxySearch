package com.zilai.zilaibuy.dto;

import com.zilai.zilaibuy.entity.VasRequestEntity;

import java.time.LocalDateTime;

public record VasRequestDto(
        Long id,
        Long userId,
        String userPhone,
        String username,
        String parcelIds,
        String orderIds,
        String services,
        String itemsSummary,
        String status,
        String adminNotes,
        String serviceResults,
        LocalDateTime createdAt
) {
    public static VasRequestDto from(VasRequestEntity e) {
        return new VasRequestDto(
                e.getId(),
                e.getUser().getId(),
                e.getUser().getPhone(),
                e.getUser().getUsername(),
                e.getParcelIds(),
                e.getOrderIds(),
                e.getServices(),
                e.getItemsSummary(),
                e.getStatus().name(),
                e.getAdminNotes(),
                e.getServiceResults(),
                e.getCreatedAt()
        );
    }
}
