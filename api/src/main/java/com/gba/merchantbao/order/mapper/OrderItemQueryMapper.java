package com.gba.merchantbao.order.mapper;

import com.gba.merchantbao.order.entity.OrderItemRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemQueryMapper {
    List<OrderItemRow> findByOrder(@Param("merchantId") long merchantId, @Param("orderId") long orderId);
}
