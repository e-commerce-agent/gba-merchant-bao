import { get } from './request'
import type { OrderDetail, OrderPage } from './types'

/**
 * 订单查询（order-v1）。
 * 消费者只查自己的订单、员工只查本商户订单，归属校验在后端；
 * 越界与不存在统一 404/10004，前端不区分「没有」和「不是你的」。
 */
export function listOrders(pageNum = 1, pageSize = 10) {
  return get<OrderPage>('/v1/orders', { pageNum, pageSize })
}

export function getOrder(orderId: string) {
  return get<OrderDetail>(`/v1/orders/${orderId}`)
}
