package com.gba.merchantbao.inventory.service;

import com.gba.merchantbao.inventory.mapper.InventoryMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tenant-scoped stock facts for callers that already enforce catalog visibility. */
@Service
public class InventoryAvailabilityQueryService {
    private final InventoryMapper inventory;

    public InventoryAvailabilityQueryService(InventoryMapper inventory) {
        this.inventory = inventory;
    }

    /** Missing and exhausted rows are false. Storage failures deliberately propagate. */
    public Map<Long, Boolean> inStock(long merchantId, List<Long> skuIds) {
        if (merchantId <= 0) throw new IllegalArgumentException("merchantId must be positive");
        List<Long> ids = List.copyOf(skuIds).stream().distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Boolean> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, false));
        for (Long id : inventory.selectInStockSkuIds(merchantId, ids)) {
            if (result.containsKey(id)) result.put(id, true);
        }
        return Map.copyOf(result);
    }
}
