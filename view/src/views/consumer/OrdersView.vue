<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, Van } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import ProductThumb from '@/components/ProductThumb.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getOrder, listOrders } from '@/api/order'
import { getOrderShipment } from '@/api/shipment'
import { ApiError } from '@/api/request'
import type {
  OrderItemView,
  OrderStatus,
  OrderSummary,
  ShipmentStatus,
  ShipmentView
} from '@/api/types'
import { formatDateTime, formatMoney } from '@/utils/format'
import { thumbOf } from '@/utils/thumb'

const router = useRouter()
const orders = ref<OrderSummary[]>([])
/** 列表只返回 Summary，卡片要展示商品行，所以按 orderId 拉详情（一页最多 10 单） */
const itemsByOrder = ref<Record<string, OrderItemView[]>>({})
const loading = ref(true)
const activeTab = ref<'ALL' | OrderStatus>('ALL')

const drawerVisible = ref(false)
const activeOrder = ref<OrderSummary | null>(null)
const shipment = ref<ShipmentView | null>(null)
const shipmentLoading = ref(false)
const shipmentError = ref('')

const PAGE_SIZE = 10

const tabs: Array<{ key: 'ALL' | OrderStatus; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING_SHIPMENT', label: '待发货' },
  { key: 'SHIPPED', label: '运输中' },
  { key: 'COMPLETED', label: '已完成' }
]

/** 订单状态：与后端 ck_trade_order_status 一一对应 */
const statusMeta: Record<OrderStatus, { label: string; tone: string }> = {
  PENDING_PAYMENT: { label: '待付款', tone: 'warn' },
  PENDING_SHIPMENT: { label: '待发货', tone: 'info' },
  SHIPPED: { label: '运输中', tone: 'brand' },
  COMPLETED: { label: '已完成', tone: 'ok' },
  CANCELLED: { label: '已取消', tone: 'muted' }
}

/** 物流状态：NOT_SHIPPED 是后端在“订单存在但无运单”时合成的，不是错误态 */
const shipmentStatusMeta: Record<ShipmentStatus, { label: string; tone: string }> = {
  NOT_SHIPPED: { label: '待发货', tone: 'info' },
  IN_TRANSIT: { label: '运输中', tone: 'brand' },
  DELIVERED: { label: '已签收', tone: 'ok' }
}

const filtered = computed(() =>
  activeTab.value === 'ALL' ? orders.value : orders.value.filter((o) => o.status === activeTab.value)
)

const tracks = computed(() =>
  [...(shipment.value?.tracks ?? [])].sort(
    (a, b) => new Date(b.eventTime).getTime() - new Date(a.eventTime).getTime()
  )
)

function countOf(key: 'ALL' | OrderStatus): number {
  return key === 'ALL' ? orders.value.length : orders.value.filter((o) => o.status === key).length
}

async function openShipment(order: OrderSummary) {
  activeOrder.value = order
  shipment.value = null
  shipmentError.value = ''
  drawerVisible.value = true
  shipmentLoading.value = true
  try {
    shipment.value = await getOrderShipment(order.orderId)
  } catch (e) {
    // 404/10004 统一文案：不区分“没有这单”和“不是你的单”，避免泄露他人订单是否存在
    shipmentError.value =
      e instanceof ApiError && (e.status === 404 || e.code === 10004)
        ? '订单不存在或无权查看'
        : e instanceof ApiError
          ? e.message
          : '物流信息加载失败，请稍后重试'
  } finally {
    shipmentLoading.value = false
  }
}

function askAboutOrder(order: OrderSummary) {
  router.push({ path: '/consumer/chat', query: { q: `帮我查一下订单 ${order.orderNo} 的物流` } })
}

onMounted(async () => {
  try {
    const page = await listOrders(1, PAGE_SIZE)
    orders.value = page.records
    // 详情失败不影响列表展示，卡片里商品行留空即可
    await Promise.all(
      page.records.map(async (o) => {
        try {
          const detail = await getOrder(o.orderId)
          itemsByOrder.value[o.orderId] = detail.items
        } catch {
          itemsByOrder.value[o.orderId] = []
        }
      })
    )
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="orders">
    <header class="orders__head">
      <div>
        <h2 class="orders__title">我的订单</h2>
        <p class="orders__desc">只展示当前账号下的订单，物流信息按订单实时向后端查询</p>
      </div>
      <el-button :icon="ChatDotRound" @click="router.push('/consumer/chat')">问 Agent</el-button>
    </header>

    <div class="orders__tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        type="button"
        class="orders__tab"
        :class="{ 'is-active': activeTab === t.key }"
        @click="activeTab = t.key"
      >
        {{ t.label }}
        <span class="num">{{ countOf(t.key) }}</span>
      </button>
    </div>

    <div v-if="loading" class="orders__list">
      <div v-for="i in 2" :key="i" class="skeleton" style="height: 180px" />
    </div>

    <EmptyState
      v-else-if="!filtered.length"
      title="这里还没有订单"
      description="下单后可以在「我的订单」查看物流轨迹；有问题也可以直接问 Agent"
    />

    <div v-else class="orders__list">
      <article v-for="o in filtered" :key="o.orderId" class="ocard">
        <header class="ocard__head">
          <div class="ocard__meta">
            <span class="ocard__no num">{{ o.orderNo }}</span>
            <span class="ocard__date num">{{ formatDateTime(o.placedAt) }}</span>
          </div>
          <span class="ocard__status" :class="`is-${statusMeta[o.status].tone}`">
            {{ statusMeta[o.status].label }}
          </span>
        </header>

        <div class="ocard__items">
          <div v-for="item in itemsByOrder[o.orderId] || []" :key="item.itemId" class="oitem">
            <div class="oitem__thumb">
              <ProductThumb :kind="thumbOf(item.productName)" :size="56" ratio="1 / 1" />
            </div>
            <div class="oitem__info">
              <div class="oitem__name">{{ item.productName }}</div>
              <div class="oitem__sku">{{ item.skuAttributes }} · ×{{ item.quantity }}</div>
            </div>
            <div class="oitem__price num">{{ formatMoney(item.amount, o.currency) }}</div>
          </div>
          <div v-if="!(itemsByOrder[o.orderId] || []).length" class="oitem oitem--empty">
            商品明细加载中或暂不可用，可点「物流详情」查看该单进度
          </div>
        </div>

        <footer class="ocard__foot">
          <div class="ocard__total">
            合计
            <span class="num">{{ formatMoney(o.totalAmount, o.currency) }}</span>
          </div>
          <div class="ocard__actions">
            <el-button size="small" :icon="ChatDotRound" @click="askAboutOrder(o)">问物流</el-button>
            <el-button size="small" :icon="Van" @click="openShipment(o)">物流详情</el-button>
          </div>
        </footer>
      </article>
    </div>

    <el-drawer v-model="drawerVisible" title="物流详情" size="420px">
      <div v-if="shipmentLoading" class="ship__loading">
        <div class="skeleton" style="height: 72px; margin-bottom: 16px" />
        <div class="skeleton" style="height: 140px" />
      </div>

      <EmptyState
        v-else-if="shipmentError"
        title="查不到物流信息"
        :description="shipmentError"
      />

      <div v-else-if="shipment" class="ship">
        <div class="ship__head">
          <div class="ship__top">
            <span class="ship__carrier">
              {{ shipment.status === 'NOT_SHIPPED' ? '尚未发货' : shipment.carrierName || '承运商未回传' }}
            </span>
            <span class="ship__badge" :class="`is-${shipmentStatusMeta[shipment.status].tone}`">
              {{ shipmentStatusMeta[shipment.status].label }}
            </span>
          </div>

          <div v-if="shipment.trackingNo" class="ship__no num">
            运单号 {{ shipment.trackingNo }}
            <span v-if="shipment.carrierCode" class="ship__code">{{ shipment.carrierCode }}</span>
          </div>

          <div v-if="shipment.status === 'NOT_SHIPPED'" class="ship__hint">
            商家还没有提交运单。发货后这里会显示承运商、运单号与轨迹，通常 1–2 天内更新。
          </div>
          <div v-else class="ship__status">
            当前状态：{{ shipmentStatusMeta[shipment.status].label }}
            <span v-if="shipment.status === 'IN_TRANSIT' && shipment.estimatedDeliveryAt" class="ship__eta">
              预计 {{ formatDateTime(shipment.estimatedDeliveryAt) }} 送达
            </span>
            <span v-else-if="shipment.deliveredAt" class="ship__eta">
              签收时间 {{ formatDateTime(shipment.deliveredAt) }}
            </span>
          </div>
        </div>

        <ul v-if="tracks.length" class="ship__timeline">
          <li
            v-for="(t, i) in tracks"
            :key="`${t.eventTime}-${i}`"
            class="ship__node"
            :class="{ 'is-latest': i === 0 }"
          >
            <span class="ship__dot" />
            <div class="ship__content">
              <div class="ship__tstatus">{{ t.description }}</div>
              <div v-if="t.location" class="ship__tdesc">{{ t.location }}</div>
              <div class="ship__ttime num">{{ formatDateTime(t.eventTime) }}</div>
            </div>
          </li>
        </ul>

        <div v-else class="ship__hint ship__hint--empty">暂无轨迹，承运商回传后会按时间倒序展示。</div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.orders__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.orders__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.orders__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.orders__tabs {
  display: flex;
  gap: 4px;
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
}

.orders__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  font-size: var(--fs-sm);
  color: var(--text-muted);
  cursor: pointer;

  span {
    font-size: var(--fs-xs);
    color: var(--text-subtle);
  }

  &:hover {
    background: var(--ink-100);
  }

  &.is-active {
    background: var(--ink-900);
    color: #fff;

    span {
      color: rgba(255, 255, 255, 0.6);
    }
  }
}

.orders__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.ocard {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.ocard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.ocard__meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.ocard__no {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-body);
}

.ocard__date {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.ocard__status {
  padding: 2px 9px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;

  &.is-brand {
    background: var(--brand-50);
    color: var(--brand-600);
  }
  &.is-ok {
    background: var(--green-50);
    color: var(--green-500);
  }
  &.is-info {
    background: var(--teal-50);
    color: var(--teal-600);
  }
  &.is-warn {
    background: var(--amber-50);
    color: var(--amber-600);
  }
  &.is-muted {
    background: var(--ink-100);
    color: var(--text-subtle);
  }
}

.ocard__items {
  padding: var(--space-2) var(--space-5);
}

.oitem {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) 0;

  & + & {
    border-top: 1px dashed var(--border-subtle);
  }
}

.oitem--empty {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.oitem__thumb {
  width: 56px;
  flex: none;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.oitem__info {
  flex: 1;
  min-width: 0;
}

.oitem__name {
  font-size: var(--fs-sm);
  color: var(--text-strong);
}

.oitem__sku {
  margin-top: 3px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.oitem__price {
  font-size: var(--fs-sm);
  color: var(--text-body);
}

.ocard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--border-subtle);
}

.ocard__total {
  font-size: var(--fs-xs);
  color: var(--text-muted);

  span {
    margin-left: 6px;
    font-size: var(--fs-lg);
    font-weight: 600;
    color: var(--ink-900);
  }
}

.ship__head {
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.ship__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.ship__carrier {
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-strong);
}

.ship__badge {
  padding: 2px 9px;
  border-radius: var(--radius-pill);
  font-size: 11px;

  &.is-brand {
    background: var(--brand-50);
    color: var(--brand-600);
  }
  &.is-ok {
    background: var(--green-50);
    color: var(--green-500);
  }
  &.is-info {
    background: var(--teal-50);
    color: var(--teal-600);
  }
}

.ship__no {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.ship__code {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--ink-100);
  color: var(--text-subtle);
}

.ship__status {
  margin-top: 8px;
  font-size: var(--fs-xs);
  color: var(--brand-600);
}

.ship__eta {
  margin-left: 6px;
  color: var(--text-muted);
}

.ship__hint {
  margin-top: 10px;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-muted);
}

.ship__hint--empty {
  margin-top: var(--space-5);
}

.ship__timeline {
  margin-top: var(--space-5);
  padding-left: 6px;
}

.ship__node {
  position: relative;
  display: flex;
  gap: var(--space-4);
  padding-bottom: var(--space-5);

  &::before {
    content: '';
    position: absolute;
    left: 4px;
    top: 12px;
    bottom: 0;
    width: 1px;
    background: var(--border-default);
  }

  &:last-child::before {
    display: none;
  }
}

.ship__dot {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--ink-200);
  flex: none;
}

.is-latest .ship__dot {
  background: var(--brand-500);
  box-shadow: 0 0 0 3px var(--brand-100);
}

.ship__tstatus {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-body);
}

.is-latest .ship__tstatus {
  color: var(--brand-600);
}

.ship__tdesc {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
  line-height: 1.7;
}

.ship__ttime {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-subtle);
}
</style>
