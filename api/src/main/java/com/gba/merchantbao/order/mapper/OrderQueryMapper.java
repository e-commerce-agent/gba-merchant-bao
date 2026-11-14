package com.gba.merchantbao.order.mapper;

import com.gba.merchantbao.order.entity.OrderRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Every statement carries merchant_id plus the caller-specific owner scope. */
@Mapper
public interface OrderQueryMapper {
    OrderRow findById(@Param("merchantId") long merchantId, @Param("orderId") long orderId);
    OrderRow findByOrderNo(@Param("merchantId") long merchantId, @Param("orderNo") String orderNo);
    OrderRow findOwnedByOrderNo(@Param("merchantId") long merchantId, @Param("orderNo") String orderNo,
                               @Param("customerId") long customerId);
    OrderRow findOwnedById(@Param("merchantId") long merchantId, @Param("orderId") long orderId,
                           @Param("customerId") long customerId);
    List<OrderRow> findByCustomer(@Param("merchantId") long merchantId, @Param("customerId") long customerId,
                                  @Param("limit") int limit, @Param("offset") int offset);
    long countByCustomer(@Param("merchantId") long merchantId, @Param("customerId") long customerId);
    List<OrderRow> findByMerchant(@Param("merchantId") long merchantId,
                                  @Param("limit") int limit, @Param("offset") int offset);
    long countByMerchant(@Param("merchantId") long merchantId);
}
