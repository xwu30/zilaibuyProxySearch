package com.zilai.zilaibuy.dto;

import java.util.List;

public record ZilaiSearchResponseDto(
        String keyword,
        int page,
        int hits,
        int totalCount,
        List<ZilaiItemDto> items
) {}
