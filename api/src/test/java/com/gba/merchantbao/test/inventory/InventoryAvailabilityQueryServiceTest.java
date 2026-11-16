package com.gba.merchantbao.test.inventory;

import com.gba.merchantbao.inventory.mapper.InventoryMapper;
import com.gba.merchantbao.inventory.service.InventoryAvailabilityQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryAvailabilityQueryServiceTest {
    private final InventoryMapper inventory = mock(InventoryMapper.class);
    private final InventoryAvailabilityQueryService availability = new InventoryAvailabilityQueryService(inventory);

    @Test void returnsOnlyRequestedBooleansAndPassesTheMerchantScope() {
        when(inventory.selectInStockSkuIds(7, List.of(11L, 12L, 13L))).thenReturn(List.of(11L));
        when(inventory.selectInStockSkuIds(8, List.of(11L, 12L, 13L))).thenReturn(List.of());
        assertThat(availability.inStock(7, List.of(11L, 12L, 13L, 11L)))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(11L, true, 12L, false, 13L, false));
        assertThat(availability.inStock(8, List.of(11L, 12L, 13L)).values()).containsOnly(false);
        verify(inventory).selectInStockSkuIds(7, List.of(11L, 12L, 13L));
    }

    @Test void emptyBatchDoesNotReadStorage() {
        assertThat(availability.inStock(7, List.of())).isEmpty();
        verifyNoInteractions(inventory);
    }

    @Test void storageFailureIsNeverTurnedIntoOutOfStock() {
        var failure = new DataAccessResourceFailureException("storage unavailable");
        when(inventory.selectInStockSkuIds(7, List.of(11L))).thenThrow(failure);
        assertThatThrownBy(() -> availability.inStock(7, List.of(11L))).isSameAs(failure);
    }
}
