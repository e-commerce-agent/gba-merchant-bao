package com.gba.merchantbao.product.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.product.cache.ProductCatalog.Product;
import com.gba.merchantbao.product.cache.ProductCatalog.Sku;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache for the public catalog — the main landing point for the "high concurrency"
 * requirement. Read-mostly public views are cached per store; every merchant write bumps a
 * per-store version key so all entries of that store invalidate in O(1) without touching auth.
 * Every Redis failure degrades silently to the database: the cache never becomes a dependency.
 */
@Service
public class ProductCacheStore {
    public static final String KIND_PRODUCT = "product";
    public static final String KIND_SKU = "sku";
    public static final String KIND_PAGE = "page";

    public static final Duration VERSION_TTL = Duration.ofHours(24);
    public static final Duration ENTRY_TTL = Duration.ofMinutes(15);
    public static final Duration MAPPING_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public ProductCacheStore(StringRedisTemplate redis, ObjectMapper mapper,
                             @Value("${merchant.product.cache-enabled:true}") boolean enabled) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    /** Store-scoped version; a write increments it and every cached entry key of that store becomes unreachable. */
    public long version(long merchantId) {
        if (!enabled) return -1;
        try {
            String value = redis.opsForValue().get(versionKey(merchantId));
            return value == null ? 1 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public void bump(long merchantId) {
        if (!enabled) return;
        try {
            String key = versionKey(merchantId);
            if (Boolean.FALSE.equals(redis.hasKey(key))) redis.opsForValue().set(key, "1", VERSION_TTL);
            else redis.opsForValue().increment(key);
        } catch (RuntimeException ignored) {
            // cache unavailable: next reads just go to the database
        }
    }

    /** storeSlug → merchantId mapping; short TTL keeps a deactivated store going dark within a minute. */
    public Long readMerchantId(String storeSlug) {
        if (!enabled) return null;
        try {
            String value = redis.opsForValue().get(mappingKey(storeSlug));
            return value == null ? null : Long.valueOf(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void putMerchantId(String storeSlug, long merchantId) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(mappingKey(storeSlug), Long.toString(merchantId), MAPPING_TTL);
        } catch (RuntimeException ignored) {
            // degrade to database
        }
    }

    public Product readProduct(long merchantId, long productId) {
        return readEntry(merchantId, KIND_PRODUCT, Long.toString(productId), Product.class);
    }

    public void putProduct(long merchantId, long productId, Product view) {
        putEntry(merchantId, KIND_PRODUCT, Long.toString(productId), view);
    }

    public Sku readSku(long merchantId, long skuId) {
        return readEntry(merchantId, KIND_SKU, Long.toString(skuId), Sku.class);
    }

    public void putSku(long merchantId, long skuId, Sku view) {
        putEntry(merchantId, KIND_SKU, Long.toString(skuId), view);
    }

    public PageEntry readPage(long merchantId, Long categoryId, int size) {
        return readEntry(merchantId, KIND_PAGE, pageKey(categoryId, size), PageEntry.class);
    }

    public void putPage(long merchantId, Long categoryId, int size, PageEntry entry) {
        putEntry(merchantId, KIND_PAGE, pageKey(categoryId, size), entry);
    }

    /** Generic entry read; returns null on miss, Redis outage or deserialization mismatch (never throws). */
    public <T> T readEntry(long merchantId, String kind, String id, Class<T> type) {
        var typeRef = mapper.constructType(type);
        return readEntry(merchantId, kind, id, type, typeRef);
    }

    private <T> T readEntry(long merchantId, String kind, String id, Class<T> type,
                            com.fasterxml.jackson.databind.JavaType javaType) {
        if (!enabled) return null;
        long ver = version(merchantId);
        if (ver < 0) return null;
        try {
            String json = redis.opsForValue().get(entryKey(merchantId, ver, kind, id));
            if (json == null) return null;
            return mapper.readValue(json, javaType);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return null; // corrupt entry behaves like a miss
        }
    }

    public void putEntry(long merchantId, String kind, String id, Object value) {
        if (!enabled || value == null) return;
        long ver = version(merchantId);
        if (ver < 0) return;
        try {
            redis.opsForValue().set(entryKey(merchantId, ver, kind, id), mapper.writeValueAsString(value), ENTRY_TTL);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // degrade to database
        }
    }

    /** First mall page per store/category (cursor-less requests only). */
    public record PageEntry(java.util.List<Product> items, String nextCursor, boolean hasMore) {
        public static PageEntry of(java.util.List<Product> items, String nextCursor, boolean hasMore) {
            return new PageEntry(items, nextCursor, hasMore);
        }
    }

    public static final String CATALOG_NAMESPACE = "gba:product:catalog:v2:";

    public static String versionKey(long merchantId) { return CATALOG_NAMESPACE + "ver:" + merchantId; }
    public static String mappingKey(String storeSlug) { return "gba:product:store:" + storeSlug; }
    public static String pageKey(Long categoryId, int size) {
        return (categoryId == null ? "0" : Long.toString(categoryId)) + ":size:" + size;
    }
    public static String entryKey(long merchantId, long version, String kind, String id) {
        return CATALOG_NAMESPACE + "e:" + merchantId + ":v" + version + ":" + kind + ":" + id;
    }
}
