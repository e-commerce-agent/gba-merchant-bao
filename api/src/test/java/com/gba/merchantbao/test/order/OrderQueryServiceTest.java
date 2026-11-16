package com.gba.merchantbao.test.order;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.order.entity.OrderItemRow;
import com.gba.merchantbao.order.entity.OrderRow;
import com.gba.merchantbao.order.exception.OrderError;
import com.gba.merchantbao.order.mapper.OrderItemQueryMapper;
import com.gba.merchantbao.order.mapper.OrderQueryMapper;
import com.gba.merchantbao.order.service.OrderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        when(orders.findOwnedByOrderNo(MERCHANT, "SO-1", OWNER)).thenReturn(row);
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

    @Test
    void ownerAndMerchantStaffCanReadDetailByOrderNumberWithItemSnapshots() {
        var now = Instant.parse("2026-11-11T00:00:00Z");
        var item = new OrderItemRow(101L, MERCHANT, ORDER, 201L, 301L, "商品交易快照", "白色",
                new BigDecimal("79.00"), 1, new BigDecimal("79.00"), now, now);
        when(items.findByOrder(MERCHANT, ORDER)).thenReturn(List.of(item));

        var consumer = service.detailByOrderNo(actor(MERCHANT, OWNER, AccountRole.CONSUMER), "SO-1");
        var staff = service.detailByOrderNo(actor(MERCHANT, OTHER, AccountRole.MERCHANT_STAFF), "SO-1");

        assertThat(staff).isEqualTo(consumer);
        assertThat(consumer.orderId().value()).isEqualTo(ORDER);
        assertThat(consumer.orderNo()).isEqualTo("SO-1");
        assertThat(consumer.status()).isEqualTo("SHIPPED");
        assertThat(consumer.payStatus()).isEqualTo("PAID");
        assertThat(consumer.totalAmount()).isEqualByComparingTo("79.00");
        assertThat(consumer.items()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.productName()).isEqualTo("商品交易快照");
            assertThat(snapshot.skuAttributes()).isEqualTo("白色");
            assertThat(snapshot.quantity()).isOne();
        });
        verify(orders).findOwnedByOrderNo(MERCHANT, "SO-1", OWNER);
        verify(orders).findByOrderNo(MERCHANT, "SO-1");
        verifyNoMoreInteractions(orders);
        verify(items, times(2)).findByOrder(MERCHANT, ORDER);
    }

    @Test
    void orderNumberDetailRejectsOtherConsumersForeignActorsAndMissingOrdersBeforeItems() {
        var other = actor(MERCHANT, OTHER, AccountRole.CONSUMER);
        var foreignConsumer = actor(FOREIGN, OWNER, AccountRole.CONSUMER);
        var foreignStaff = actor(FOREIGN, OTHER, AccountRole.MERCHANT_STAFF);
        assertNotFound(() -> service.detailByOrderNo(other, "SO-1"));
        assertNotFound(() -> service.detailByOrderNo(foreignConsumer, "SO-1"));
        assertNotFound(() -> service.detailByOrderNo(foreignStaff, "SO-1"));
        assertNotFound(() -> service.detailByOrderNo(actor(MERCHANT, OWNER, AccountRole.CONSUMER), "missing"));
        assertNotFound(() -> service.detailByOrderNo(actor(MERCHANT, OTHER, AccountRole.MERCHANT_STAFF), "missing"));

        verify(orders).findOwnedByOrderNo(MERCHANT, "SO-1", OTHER);
        verify(orders).findOwnedByOrderNo(FOREIGN, "SO-1", OWNER);
        verify(orders).findByOrderNo(FOREIGN, "SO-1");
        verify(orders).findOwnedByOrderNo(MERCHANT, "missing", OWNER);
        verify(orders).findByOrderNo(MERCHANT, "missing");
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(items);
    }

    @Test
    void orderNumberDetailRejectsMissingActorAndInvalidNumbersWithoutReadingStorage() {
        assertNotFound(() -> service.detailByOrderNo(null, "SO-1"));
        assertNotFound(() -> service.detailByOrderNo(new Actor(null, "sid"), "SO-1"));
        var consumer = actor(MERCHANT, OWNER, AccountRole.CONSUMER);
        for (String number : new String[]{null, "", " ", " SO-1", "SO-1 ", "SO\n1", "SO'1", "x".repeat(65)}) {
            assertNotFound(() -> service.detailByOrderNo(consumer, number));
        }
        verifyNoInteractions(orders, items);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(OrderError.class, error -> {
            assertThat(error.status()).isEqualTo(404);
            assertThat(error.code().getCode()).isEqualTo(10004);
        });
    }
}
