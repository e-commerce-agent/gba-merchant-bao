package com.gba.merchantbao.product.service;

import com.gba.merchantbao.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Tenant-scoped catalog facts, independent of public visibility, caching and stock. */
@Service
public class ProductReferenceService {
    private final ProductSkuMapper skuMapper;

    public ProductReferenceService(ProductSkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    public record SkuReference(long merchantId, long productId, long skuId, String status) {
        public boolean active() { return "ACTIVE".equals(status); }
    }

    /** merchantId must come from a trusted actor or server-side tenant resolution. */
    public Optional<SkuReference> findSku(long merchantId, long skuId) {
        var sku = skuMapper.find(merchantId, skuId);
        if (sku == null || sku.merchantId() != merchantId) {
            return Optional.empty();
        }
        // DRAFT/UNPUBLISHED parent products still allow staff to prepare stock.
        return Optional.of(new SkuReference(sku.merchantId(), sku.productId(), sku.id(), sku.status()));
    }
}
