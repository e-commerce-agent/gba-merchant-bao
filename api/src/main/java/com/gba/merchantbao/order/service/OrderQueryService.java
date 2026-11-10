package com.gba.merchantbao.order.service;

import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.order.entity.OrderItemRow;
import com.gba.merchantbao.order.entity.OrderRow;
import com.gba.merchantbao.order.exception.OrderError;
import com.gba.merchantbao.order.mapper.OrderItemQueryMapper;
import com.gba.merchantbao.order.mapper.OrderQueryMapper;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-only order queries for consumers (own orders), staff (own merchant orders) and the Agent
 * order Tool. Identity comes from the trusted Actor only; out-of-scope and missing resources are
 * indistinguishable 404s. Item snapshots are returned as frozen at placement; no phone or address
 * is part of the order model.
 */
@Service
public class OrderQueryService {
    private final OrderQueryMapper orders;
    private final OrderItemQueryMapper items;

    public OrderQueryService(OrderQueryMapper orders, OrderItemQueryMapper items) {
        this.orders = orders;
        this.items = items;
    }

    public Page list(Actor actor, int pageNum, int pageSize) {
        var principal = actor.principal();
        int limit = pageSize;
        int offset = (pageNum - 1) * pageSize;
        if (principal.role() == AccountRole.CONSUMER) {
            long total = orders.countByCustomer(principal.merchantId(), principal.accountId());
            var records = orders.findByCustomer(principal.merchantId(), principal.accountId(), limit, offset)
                    .stream().map(OrderQueryService::summary).toList();
            return new Page(records, total, pageNum, pageSize);
        }
        if (principal.role() == AccountRole.MERCHANT_STAFF) {
            long total = orders.countByMerchant(principal.merchantId());
            var records = orders.findByMerchant(principal.merchantId(), limit, offset)
                    .stream().map(OrderQueryService::summary).toList();
            return new Page(records, total, pageNum, pageSize);
        }
        throw OrderError.notFound();
    }

    public Detail detail(Actor actor, long orderId) {
        var principal = actor.principal();
        OrderRow row;
        if (principal.role() == AccountRole.CONSUMER) {
            row = orders.findOwnedById(principal.merchantId(), orderId, principal.accountId());
        } else if (principal.role() == AccountRole.MERCHANT_STAFF) {
            row = orders.findById(principal.merchantId(), orderId);
        } else {
            row = null;
        }
        if (row == null) throw OrderError.notFound();
        var itemRows = items.findByOrder(principal.merchantId(), row.id());
        return detail(row, itemRows);
    }

    static Summary summary(OrderRow row) {
        return new Summary(new ApiId(row.id()), row.orderNo(), row.status(), row.payStatus(),
                row.totalAmount(), row.paidAmount(), row.currency(), row.placedAt());
    }

    static Detail detail(OrderRow row, List<OrderItemRow> itemRows) {
        var views = itemRows.stream().map(OrderQueryService::item).toList();
        return new Detail(new ApiId(row.id()), row.orderNo(), row.status(), row.payStatus(),
                row.totalAmount(), row.paidAmount(), row.currency(), row.placedAt(), views);
    }

    static ItemView item(OrderItemRow row) {
        return new ItemView(new ApiId(row.id()), new ApiId(row.productId()), new ApiId(row.skuId()),
                row.productNameSnapshot(), row.skuAttrsSnapshot(), row.unitPrice(), row.quantity(), row.amount());
    }

    public record Summary(ApiId orderId, String orderNo, String status, String payStatus,
                          BigDecimal totalAmount, BigDecimal paidAmount, String currency, Instant placedAt) { }

    public record ItemView(ApiId itemId, ApiId productId, ApiId skuId, String productName, String skuAttributes,
                           BigDecimal unitPrice, int quantity, BigDecimal amount) { }

    public record Detail(ApiId orderId, String orderNo, String status, String payStatus,
                         BigDecimal totalAmount, BigDecimal paidAmount, String currency, Instant placedAt,
                         List<ItemView> items) { }

    public record Page(List<Summary> records, long total, int current, int size) { }
}
