package com.gba.merchantbao.shipment.mapper;

import com.gba.merchantbao.shipment.entity.ShipmentRow;
import com.gba.merchantbao.shipment.entity.ShipmentTrackRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Every operation requires a server-resolved merchant scope; no global shipment lookup. */
@Mapper
public interface ShipmentMapper {
    ShipmentRow findByOrder(@Param("merchantId") long merchantId, @Param("orderId") long orderId);
    ShipmentRow findByOrderNo(@Param("merchantId") long merchantId, @Param("orderNo") String orderNo);
    List<ShipmentTrackRow> findTracks(@Param("merchantId") long merchantId, @Param("shipmentId") long shipmentId);
}
