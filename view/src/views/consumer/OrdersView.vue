<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, Refresh, Van } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import ProductThumb from '@/components/ProductThumb.vue'
import EmptyState from '@/components/EmptyState.vue'
import ShipmentPanel from '@/components/ShipmentPanel.vue'
import { getOrder, listOrders } from '@/api/order'
import type { OrderItemView, OrderStatus, OrderSummary } from '@/api/types'
import { formatDateTime, formatMoney } from '@/utils/format'
import { thumbOf } from '@/utils/thumb'

const router = useRouter()
const orders = ref<OrderSummary[]>([])
/** 列表只返回 Summary，卡片要展示商品行，所以按 orderId 拉详情（一页最多 10 单） */
const itemsByOrder = ref<Record<string, OrderItemView[]>>({})
const loading = ref(true)
const activeTab = ref<'ALL' | OrderStatus>('ALL')

/** 物流抽屉：面板自己按 orderId 拉 shipment-v1，页面只负责开关 */
const drawerVisible = ref(false)
const activeOrder = ref<OrderSummary | null>(null)

const PAGE_SIZE = 10
/** order-v1 用 pageNum/pageSize，返回 records + total，可据此做「加载更多」 */
const pageNo = ref(1)
const total = ref(0)
const loadingMore = ref(false)
const hasMore = computed(() => orders.value.length < total.value)

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

const filtered = computed(() =>
  activeTab.value === 'ALL' ? orders.value : orders.value.filter((o) => o.status === activeTab.value)
)

function openShipment(order: OrderSummary) {
  activeOrder.value = order
  drawerVisible.value = true
}

function askAboutOrder(order: OrderSummary) {
  router.push({ path: '/consumer/chat', query: { q: `帮我查一下订单 ${order.orderNo} 的物流` } })
}

/** 列表只返回 Summary，卡片要展示商品行，逐单补详情；单条失败不影响列表 */
async function fillItems(records: OrderSummary[]) {
  await Promise.all(
    records.map(async (o) => {
      try {
        const detail = await getOrder(o.orderId)
        itemsByOrder.value[o.orderId] = detail.items
      } catch {
        itemsByOrder.value[o.orderId] = []
      }
    })
  )
}

async function load(reset = false) {
  if (reset) {
    pageNo.value = 1
    orders.value = []
    itemsByOrder.value = {}
  }
  try {
    const page = await listOrders(pageNo.value, PAGE_SIZE)
    total.value = page.total
    orders.value = reset ? page.records : [...orders.value, ...page.records]
    await fillItems(page.records)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  pageNo.value += 1
  await load()
}

function refresh() {
  loading.value = true
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="orders">
    <header class="orders__head">
      <div>
        <h2 class="orders__title">我的订单</h2>
        <p class="orders__desc">
          只展示当前账号下的订单，物流信息按订单实时向后端查询
          <span v-if="total" class="num">共 {{ total }} 单</span>
        </p>
      </div>
      <div class="orders__actions">
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button :icon="ChatDotRound" @click="router.push('/consumer/chat')">问 Agent</el-button>
      </div>
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
      </button>
    </div>

    <div v-if="loading && !orders.length" class="orders__list">
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

    <div v-if="!loading && hasMore" class="orders__more">
      <el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
      <span class="orders__more-hint num">已显示 {{ orders.length }} / {{ total }}</span>
    </div>

    <el-drawer v-model="drawerVisible" title="物流详情" size="420px">
      <ShipmentPanel v-if="activeOrder" :order-id="activeOrder.orderId" />
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

.orders__actions {
  display: flex;
  gap: var(--space-2);
}

.orders__more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: var(--space-5);
}

.orders__more-hint {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
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

</style>
