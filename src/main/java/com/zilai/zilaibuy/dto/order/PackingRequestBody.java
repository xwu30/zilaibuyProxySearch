package com.zilai.zilaibuy.dto.order;

import java.util.List;

public record PackingRequestBody(List<Long> orderItemIds, List<Long> parcelIds) {}
