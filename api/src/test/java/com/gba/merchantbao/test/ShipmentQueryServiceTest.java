package com.gba.merchantbao.test;

import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.shipment.dto.ShipmentView;
import com.gba.merchantbao.shipment.entity.ShipmentRow;
import com.gba.merchantbao.shipment.entity.ShipmentTrackRow;
import com.gba.merchantbao.shipment.enums.ShipmentStatus;
import com.gba.merchantbao.shipment.mapper.ShipmentMapper;
import com.gba.merchantbao.shipment.service.OrderAccessGuard;
import com.gba.merchantbao.shipment.service.ShipmentQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Plain unit test: authorization contract of the shared query service, no Spring context. */
class ShipmentQueryServiceTest {

    private static final long MERCHANT = 1000000000000001L;
    private static final long ORDER = 3000000000000301L;

    private ShipmentMapper mapper;
    private OrderAccessGuard guard;
    private TenantResolver tenants;
    private ShipmentQueryService service;
    private Actor actor;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(ShipmentMapper.class);
        guard = Mockito.mock(OrderAccessGuard.class);
        tenants = Mockito.mock(TenantResolver.class);
        service = new ShipmentQueryService(mapper, guard, tenants);
        actor = new Actor(new AuthPrincipal(2100000000000101L, MERCHANT, AccountRole.MERCHANT_STAFF), "sid");
    }

    private ShipmentRow row() {
        return new ShipmentRow(500L, MERCHANT, ORDER, "SO-DEMO-0301", "IN_TRANSIT",
                "顺丰速运", "SF", "SF1000000000301", Instant.parse("2026-11-09T00:00:00Z"),
                null, Instant.now(), Instant.now());
    }

    private static ShipmentTrackRow track(long id, Instant time) {
        return new ShipmentTrackRow(id, 500L, MERCHANT, time, "IN_TRANSIT", "desc-" + id, "深圳", Instant.now());
    }

    @Test
    void foundShipmentReturnsViewWithTenantScopedTracks() {
        when(mapper.findByOrder(MERCHANT, ORDER)).thenReturn(row());
        when(mapper.findTracks(MERCHANT, 500L)).thenReturn(
                List.of(track(1, Instant.parse("2026-11-07T02:00:00Z")),
                        track(2, Instant.parse("2026-11-07T08:00:00Z"))));

        ShipmentView view = service.getForActor(actor, ORDER);

        assertThat(view.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(view.orderId().asString()).isEqualTo(String.valueOf(ORDER));
        assertThat(view.trackingNo()).isEqualTo("SF1000000000301");
        assertThat(view.tracks()).hasSize(2);
        assertThat(view.tracks().get(0).eventTime()).isBefore(view.tracks().get(1).eventTime());
        Mockito.verify(mapper).findByOrder(MERCHANT, ORDER);
    }

    @Test
    void missingShipmentWithUnresolvableOrderIsUnified404() {
        when(mapper.findByOrder(anyLong(), anyLong())).thenReturn(null);
        when(guard.resolve(Mockito.any(), anyLong())).thenReturn(new OrderAccessGuard.OrderAccess(false, null));

        assertThatThrownBy(() -> service.getForActor(actor, ORDER))
                .isInstanceOf(AuthError.class)
                .satisfies(e -> assertThat(((AuthError) e).status()).isEqualTo(404));
    }

    @Test
    void missingShipmentWithKnownOrderSynthesizesNotShipped() {
        when(mapper.findByOrder(anyLong(), anyLong())).thenReturn(null);
        when(guard.resolve(Mockito.any(), anyLong())).thenReturn(new OrderAccessGuard.OrderAccess(true, "SO-1"));

        ShipmentView view = service.getForActor(actor, ORDER);

        assertThat(view.status()).isEqualTo(ShipmentStatus.NOT_SHIPPED);
        assertThat(view.tracks()).isEmpty();
        assertThat(view.carrierName()).isNull();
        assertThat(view.trackingNo()).isNull();
    }

    @Test
    void storageFailureIsInternalErrorWithoutLeakage() {
        when(mapper.findByOrder(anyLong(), anyLong())).thenThrow(new QueryTimeoutException("timeout details"));

        assertThatThrownBy(() -> service.getForActor(actor, ORDER))
                .isInstanceOf(AuthError.class)
                .satisfies(e -> {
                    assertThat(((AuthError) e).status()).isEqualTo(500);
                    assertThat(e.getMessage()).doesNotContain("timeout details");
                });
    }

    @Test
    void soapPathResolvesTenantFromCodeAndRejectsUnknowns() {
        when(tenants.resolveActive("M_3C_DEMO"))
                .thenReturn(Optional.of(new Tenant(MERCHANT, "M_3C_DEMO")));
        when(mapper.findByOrderNo(MERCHANT, "SO-DEMO-0301")).thenReturn(row());
        when(mapper.findTracks(MERCHANT, 500L)).thenReturn(List.of());

        assertThat(service.getForMerchantCode("M_3C_DEMO", "SO-DEMO-0301").status())
                .isEqualTo(ShipmentStatus.IN_TRANSIT);

        when(tenants.resolveActive("M_UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForMerchantCode("M_UNKNOWN", "SO-DEMO-0301"))
                .isInstanceOf(AuthError.class)
                .satisfies(e -> assertThat(((AuthError) e).status()).isEqualTo(404));

        when(mapper.findByOrderNo(MERCHANT, "missing")).thenReturn(null);
        assertThatThrownBy(() -> service.getForMerchantCode("M_3C_DEMO", "missing"))
                .isInstanceOf(AuthError.class)
                .satisfies(e -> assertThat(((AuthError) e).status()).isEqualTo(404));
    }

    @Test
    void soapPathNeverTouchesShipmentDataWithoutTenantScope() {
        when(tenants.resolveActive(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForMerchantCode("anything", "any-order"))
                .isInstanceOf(AuthError.class);

        Mockito.verifyNoInteractions(mapper);
    }
}
