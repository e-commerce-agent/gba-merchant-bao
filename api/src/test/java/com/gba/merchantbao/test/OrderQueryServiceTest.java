package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.order.entity.OrderRow;
import com.gba.merchantbao.order.exception.OrderError;
import com.gba.merchantbao.order.mapper.OrderItemQueryMapper;
import com.gba.merchantbao.order.mapper.OrderQueryMapper;
import com.gba.merchantbao.order.service.OrderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderQueryServiceTest {
    private static final long MERCHANT = 1L, FOREIGN = 2L, OWNER = 10L, OTHER = 11L, ORDER = 100L;
    private final OrderQueryMapper orders = mock(OrderQueryMapper.class);
    private final OrderItemQueryMapper items = mock(OrderItemQueryMapper.class);
    private final OrderQueryService service = new OrderQueryService(orders, items);

    private Actor actor(long merchant, long account, AccountRole role) {
        return new Actor(new AuthPrincipal(account, merchant, role), "sid");
    }

    @BeforeEach
    void ordersVisibleOnlyWithinOwnerAndMerchantScope() {
        var now = Instant.parse("2026-11-11T00:00:00Z");
        var row = new OrderRow(ORDER, MERCHANT, "SO-1", OWNER, "SHIPPED", "PAID",
                new BigDecimal("79.00"), new BigDecimal("79.00"), "CNY", now, now, now, 0);
        when(orders.findOwnedById(MERCHANT, ORDER, OWNER)).thenReturn(row);
        when(orders.findById(MERCHANT, ORDER)).thenReturn(row);
        when(orders.findByOrderNo(MERCHANT, "SO-1")).thenReturn(row);
    }

    @Test
    void ownerAndMerchantStaffCanLocateWithoutLoadingItems() {
        var expected = new OrderQueryService.OrderLocation(MERCHANT, ORDER, "SO-1");
        assertThat(service.locateForActor(actor(MERCHANT, OWNER, AccountRole.CONSUMER), ORDER)).isEqualTo(expected);
        assertThat(service.locateForActor(actor(MERCHANT, OTHER, AccountRole.MERCHANT_STAFF), ORDER)).isEqualTo(expected);
        verify(orders).findOwnedById(MERCHANT, ORDER, OWNER);
        verify(orders).findById(MERCHANT, ORDER);
        verifyNoInteractions(items);
    }

    @Test
    void otherConsumerForeignActorsAndUnknownOrderHaveSame404() {
        for (var actor : new Actor[]{actor(MERCHANT, OTHER, AccountRole.CONSUMER),
                actor(FOREIGN, OWNER, AccountRole.CONSUMER), actor(FOREIGN, OTHER, AccountRole.MERCHANT_STAFF)}) {
            assertNotFound(() -> service.locateForActor(actor, ORDER));
        }
        assertNotFound(() -> service.locateForActor(actor(MERCHANT, OWNER, AccountRole.CONSUMER), 999L));
        assertNotFound(() -> service.locateForActor(actor(MERCHANT, OTHER, AccountRole.MERCHANT_STAFF), 999L));
        verifyNoInteractions(items);
    }

    @Test
    void serviceLookupUsesMerchantAndOrderNumberWithoutConsumerScope() {
        assertThat(service.locateForService(MERCHANT, "SO-1"))
                .isEqualTo(new OrderQueryService.OrderLocation(MERCHANT, ORDER, "SO-1"));
        assertNotFound(() -> service.locateForService(FOREIGN, "SO-1"));
        assertNotFound(() -> service.locateForService(MERCHANT, "missing"));
        verify(orders).findByOrderNo(MERCHANT, "SO-1");
        verify(orders).findByOrderNo(FOREIGN, "SO-1");
        verify(orders).findByOrderNo(MERCHANT, "missing");
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(items);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(OrderError.class, error -> {
            assertThat(error.status()).isEqualTo(404);
            assertThat(error.code().getCode()).isEqualTo(10004);
        });
    }
}
