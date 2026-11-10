package com.gba.merchantbao.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** GET /api/v1/orders query. Defaults and caps follow the v1 pagination convention. */
public record OrderListQuery(
        @Min(value = 1, message = "pageNum must be >= 1") Integer pageNum,
        @Min(value = 1, message = "pageSize must be >= 1") @Max(value = 100, message = "pageSize must be <= 100") Integer pageSize) {

    public int page() { return pageNum == null ? 1 : pageNum; }
    public int size() { return pageSize == null ? 10 : pageSize; }
    public int offset() { return (page() - 1) * size(); }
}
