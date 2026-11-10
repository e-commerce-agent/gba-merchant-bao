import { get } from './request'
import type { ShipmentView } from './types'

/**
 * 按订单查物流（shipment-v1）。
 * 只能按 orderId 查，运单号不是查询键；订单不存在 / 跨租户 / 非本人统一 404 + 10004。
 * 订单存在但未发货时后端不报错，返回 status=NOT_SHIPPED 且 tracks 为空数组。
 */
export function getOrderShipment(orderId: string) {
  return get<ShipmentView>(`/v1/orders/${orderId}/shipment`)
}
