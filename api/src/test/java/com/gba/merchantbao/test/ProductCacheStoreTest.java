package com.gba.merchantbao.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.product.cache.ProductCacheStore;
import com.gba.merchantbao.product.vo.ProductViews.PageView;
import com.gba.merchantbao.product.vo.ProductViews.PublicProductView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cache contract: version bump invalidates, outages degrade to null (never throw), JSON round-trips. */
class ProductCacheStoreTest {

    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void versionDefaultsToOneAndBumpIncrements() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(ProductCacheStore.versionKey(7))).thenReturn(null);
        var store = new ProductCacheStore(redis, mapper, true);
        assertThat(store.version(7)).isEqualTo(1);
        when(redis.hasKey(ProductCacheStore.versionKey(7))).thenReturn(true);
        when(values.get(ProductCacheStore.versionKey(7))).thenReturn("3");
        assertThat(store.version(7)).isEqualTo(3);
    }

    @Test void bumpOnFreshKeySetsOneAndOnExistingKeyIncrements() {
        when(redis.opsForValue()).thenReturn(values);
        var store = new ProductCacheStore(redis, mapper, true);
        when(redis.hasKey(ProductCacheStore.versionKey(7))).thenReturn(false);
        store.bump(7);
        verify(values).set(ProductCacheStore.versionKey(7), "1", ProductCacheStore.VERSION_TTL);
        when(redis.hasKey(ProductCacheStore.versionKey(7))).thenReturn(true);
        store.bump(7);
        verify(values).increment(ProductCacheStore.versionKey(7));
    }

    @Test void entryRoundTripsThroughJson() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        var store = new ProductCacheStore(redis, mapper, true);
        when(values.get(ProductCacheStore.versionKey(7))).thenReturn("1");
        var view = new PublicProductView(new ApiId(2600000000000201L), "P-CHG-65W", "65W 充电器",
                "GbaPower", "x", java.util.Map.of("功率", "65W"), List.of());
        var json = mapper.writeValueAsString(view);
        when(values.get(ProductCacheStore.entryKey(7, 1, ProductCacheStore.KIND_PRODUCT, "2600000000000201")))
                .thenReturn(json);
        var hit = store.readProduct(7, 2600000000000201L);
        assertThat(hit).isNotNull();
        assertThat(hit.productId().asString()).isEqualTo("2600000000000201");
        assertThat(hit.name()).isEqualTo("65W 充电器");
        store.putProduct(7, 2600000000000201L, view);
        verify(values).set(eq(ProductCacheStore.entryKey(7, 1, ProductCacheStore.KIND_PRODUCT, "2600000000000201")),
                eq(json), eq(ProductCacheStore.ENTRY_TTL));
    }

    @Test void redisOutageDegradesToNullWithoutThrowing() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        var store = new ProductCacheStore(redis, mapper, true);
        assertThat(store.version(7)).isEqualTo(-1);
        assertThat(store.readMerchantId("M_3C_DEMO")).isNull();
        assertThat(store.readProduct(7, 1L)).isNull();
        store.bump(7); // must not throw
        store.putMerchantId("M_3C_DEMO", 7L); // must not throw
    }

    @Test void pageEntryRoundTripsWithItems() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        var store = new ProductCacheStore(redis, mapper, true);
        when(values.get(ProductCacheStore.versionKey(7))).thenReturn("1");
        var entry = ProductCacheStore.PageEntry.of(
                List.of(new PublicProductView(new ApiId(1L), "C1", "充电器", null, null, java.util.Map.of(), List.of())),
                null, false);
        var json = mapper.writeValueAsString(entry);
        when(values.get(ProductCacheStore.entryKey(7, 1, ProductCacheStore.KIND_PAGE, "0"))).thenReturn(json);
        var hit = store.readPage(7, null);
        assertThat(hit.items()).hasSize(1);
        assertThat(hit.hasMore()).isFalse();
    }

    @Test void disabledStoreNeverTouchesRedis() {
        var store = new ProductCacheStore(redis, mapper, false);
        store.bump(7);
        store.putMerchantId("M", 1L);
        store.putEntry(1, "product", "1", new Object());
        assertThat(store.enabled()).isFalse();
        verify(values, org.mockito.Mockito.never()).set(anyString(), anyString(), eq(Duration.ofMinutes(1)));
    }
}
