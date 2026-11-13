<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Van, View } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import ShipmentPanel from '@/components/ShipmentPanel.vue'
import { getOrder, listOrders } from '@/api/order'
import { ApiError } from '@/api/request'
import type { OrderDetail, OrderStatus, OrderSummary } from '@/api/types'
import { formatDateTime, formatMoney } from '@/utils/format'

/**
 * 商户端订单管理（order-v1）。
 * 与消费者页共用同一个接口：后端按主体判定范围——员工只返回本商户订单，越界统一 404/10004。
 * 契约没有按状态/订单号查询的参数，筛选只作用于当前页（页面上已注明）。
 */
const rows = ref<OrderSummary[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const keyword = ref('')

const detailVisible = ref(false)
const detail = ref<OrderDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const shipVisible = ref(false)
const shipOrderId = ref('')

/** 订单状态：与后端 ck_trade_order_status 一一对应 */
const statusMeta: Record<OrderStatus, { label: string; tone: string }> = {
  PENDING_PAYMENT: { label: '待付款', tone: 'warn' },
  PENDING_SHIPMENT: { label: '待发货', tone: 'info' },
  SHIPPED: { label: '运输中', tone: 'brand' },
  COMPLETED: { label: '已完成', tone: 'ok' },
  CANCELLED: { label: '已取消', tone: 'muted' }
}

const payLabel: Record<string, string> = {
  UNPAID: '未支付',
  PAID: '已支付',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((o) => o.orderNo.toLowerCase().includes(q))
})

async function load() {
  loading.value = true
  try {
    const page = await listOrders(pageNo.value, pageSize.value)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function openDetail(row: OrderSummary) {
  detailVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await getOrder(row.orderId)
  } catch (e) {
    detailError.value =
      e instanceof ApiError && (e.status === 404 || e.code === 10004)
        ? '订单不存在或无权查看'
        : e instanceof ApiError
          ? e.message
          : '订单详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function openShipment(row: OrderSummary) {
  shipOrderId.value = row.orderId
  shipVisible.value = true
}

function onPageChange(page: number) {
  pageNo.value = page
  load()
}

function onSizeChange(size: number) {
  pageSize.value = size
  pageNo.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="morders">
    <header class="morders__head">
      <div>
        <h2 class="morders__title">订单管理</h2>
        <p class="morders__desc">
          只返回本商户的订单，物流按订单实时查询 · 共 <span class="num">{{ total }}</span> 单
        </p>
      </div>
      <div class="morders__actions">
        <el-input
          v-model="keyword"
          placeholder="按订单号筛选本页"
          clearable
          :prefix-icon="View"
          style="width: 210px"
        />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="filtered" row-key="orderId" class="morders__table">
      <el-table-column label="订单号" min-width="190">
        <template #default="{ row }">
          <span class="mono">{{ row.orderNo }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span class="tag" :class="`is-${statusMeta[row.status as OrderStatus].tone}`">
            {{ statusMeta[row.status as OrderStatus].label }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="支付" width="100">
        <template #default="{ row }">
          <span class="morders__pay">{{ payLabel[row.payStatus] || row.payStatus }}</span>
        </template>
      </el-table-column>

      <el-table-column label="订单金额" width="130" align="right">
        <template #default="{ row }">
          <span class="num">{{ formatMoney(row.totalAmount, row.currency) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="下单时间" width="170">
        <template #default="{ row }">
          <span class="num morders__time">{{ formatDateTime(row.placedAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="170" align="right">
        <template #default="{ row }">
          <el-button size="small" :icon="View" @click="openDetail(row)">详情</el-button>
          <el-button size="small" :icon="Van" @click="openShipment(row)">物流</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState
          title="还没有订单"
          description="消费者下单后订单会出现在这里；可按订单号在本页内筛选"
        />
      </template>
    </el-table>

    <div v-if="total > pageSize" class="morders__pager">
      <el-pagination
        :current-page="pageNo"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="sizes, prev, pager, next, total"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-drawer v-model="detailVisible" title="订单详情" size="460px">
      <div v-if="detailLoading">
        <div class="skeleton" style="height: 64px; margin-bottom: 16px" />
        <div class="skeleton" style="height: 160px" />
      </div>

      <EmptyState v-else-if="detailError" title="查不到订单" :description="detailError" />

      <div v-else-if="detail" class="odetail">
        <div class="odetail__head">
          <span class="mono">{{ detail.orderNo }}</span>
          <span class="tag" :class="`is-${statusMeta[detail.status].tone}`">
            {{ statusMeta[detail.status].label }}
          </span>
        </div>

        <dl class="odetail__meta">
          <div><dt>下单时间</dt><dd class="num">{{ formatDateTime(detail.placedAt) }}</dd></div>
          <div><dt>支付状态</dt><dd>{{ payLabel[detail.payStatus] || detail.payStatus }}</dd></div>
          <div><dt>订单金额</dt><dd class="num">{{ formatMoney(detail.totalAmount, detail.currency) }}</dd></div>
          <div><dt>实付金额</dt><dd class="num">{{ formatMoney(detail.paidAmount, detail.currency) }}</dd></div>
        </dl>

        <div class="odetail__items">
          <div v-for="item in detail.items" :key="item.itemId" class="oitem">
            <div class="oitem__main">
              <div class="oitem__name">{{ item.productName }}</div>
              <div class="oitem__sku">{{ item.skuAttributes }}</div>
            </div>
            <div class="oitem__qty num">×{{ item.quantity }}</div>
            <div class="oitem__price num">{{ formatMoney(item.amount, detail.currency) }}</div>
          </div>
        </div>

        <p class="odetail__note">
          以上为下单时冻结的名称、属性与单价快照，后续改价不影响历史订单。
        </p>
      </div>
    </el-drawer>

    <el-drawer v-model="shipVisible" title="物流详情" size="420px">
      <ShipmentPanel v-if="shipVisible && shipOrderId" :order-id="shipOrderId" />
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.morders__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.morders__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.morders__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.morders__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.morders__table {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.morders__pay {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.morders__time {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.morders__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}

.tag {
  display: inline-block;
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

.odetail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
}

.odetail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
  margin: var(--space-4) 0;

  dt {
    font-size: var(--fs-xs);
    color: var(--text-subtle);
  }

  dd {
    margin-top: 2px;
    font-size: var(--fs-sm);
    color: var(--text-strong);
  }
}

.odetail__items {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.oitem {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;

  & + & {
    border-top: 1px dashed var(--border-subtle);
  }
}

.oitem__main {
  flex: 1;
  min-width: 0;
}

.oitem__name {
  font-size: var(--fs-sm);
  color: var(--text-body);
}

.oitem__sku {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.oitem__qty,
.oitem__price {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.odetail__note {
  margin-top: var(--space-3);
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-subtle);
}
</style>
