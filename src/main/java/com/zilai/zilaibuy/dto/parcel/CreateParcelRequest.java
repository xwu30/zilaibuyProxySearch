package com.zilai.zilaibuy.dto.parcel;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateParcelRequest(
        String inboundTrackingNo,
        String carrier,
        @NotBlank String content,
        BigDecimal declaredValue,
        String processingOption,
        String notes
) {}
