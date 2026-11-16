package com.gba.merchantbao.test.product;

import com.gba.merchantbao.product.entity.ProductSkuRow;
import com.gba.merchantbao.product.mapper.ProductSkuMapper;
import com.gba.merchantbao.product.service.ProductReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductReferenceServiceTest {
    private final ProductSkuMapper skus = mock(ProductSkuMapper.class);
    private final ProductReferenceService service = new ProductReferenceService(skus);

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DISCONTINUED"})
    void returnsOwnershipAndStatusWithoutApplyingPublicProductVisibility(String status) {
        // No parent visibility query: even a draft/unpublished product's SKU is a catalog fact.
        when(skus.find(1L, 3L)).thenReturn(sku(1L, status));
        var reference = service.findSku(1L, 3L).orElseThrow();
        assertThat(reference.merchantId()).isEqualTo(1L);
        assertThat(reference.productId()).isEqualTo(2L);
        assertThat(reference.skuId()).isEqualTo(3L);
        assertThat(reference.status()).isEqualTo(status);
        assertThat(reference.active()).isEqualTo("ACTIVE".equals(status));
        verify(skus).find(1L, 3L);
        verifyNoMoreInteractions(skus);
    }

    @Test void missingAndForeignSkusAreInvisible() {
        assertThat(service.findSku(1L, 3L)).isEmpty();
        when(skus.find(1L, 3L)).thenReturn(sku(9L, "ACTIVE"));
        assertThat(service.findSku(1L, 3L)).isEmpty();
    }

    private ProductSkuRow sku(long merchantId, String status) {
        return new ProductSkuRow(3L, merchantId, 2L, "sku", "title", BigDecimal.ONE,
                "{}", status, null, null);
    }
}
