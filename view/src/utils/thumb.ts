import type { ProductThumbKey } from '@/api/types'

/**
 * 订单项只有成交快照（商品名 + SKU 属性），没有图片字段；
 * 展示层按商品名猜一个插画占位，接入真实图片后直接换成 <img :src="imageUrl">。
 */
const RULES: Array<[RegExp, ProductThumbKey]> = [
  [/充电宝|移动电源|磁吸电池/, 'powerbank'],
  [/数据线|线材|编织线|转接线/, 'cable'],
  [/耳机|耳麦|降噪/, 'earphone'],
  [/车载|车充/, 'car'],
  [/无线充|磁吸充/, 'wireless'],
  [/拓展坞|支架|底座|集线/, 'dock']
]

export function thumbOf(productName: string): ProductThumbKey {
  for (const [pattern, key] of RULES) {
    if (pattern.test(productName)) return key
  }
  return 'charger'
}
