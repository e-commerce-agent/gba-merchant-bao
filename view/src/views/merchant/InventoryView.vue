<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, Sort } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import { adjustInventory, listInventory, newIdempotentKey } from '@/api/inventory'
import { getSku } from '@/api/catalog'
import type { InventoryVO, SkuView } from '@/api/types'

const route = useRoute()

/**
 * 库存管理（inventory-v1，仅 MERCHANT_STAFF）。
 * 读：GET /v1/inventory/page（pageNo/pageSize，返回 records/total/current/size）
 * 写：POST /v1/inventory/adjust（idempotentKey 幂等，负数为盘亏，不得低于零）
 * 库存行只有 skuId，SKU 名称按需用 GET /v1/skus/{skuId} 补齐，失败不影响数量展示。
 */
const PAGE_SIZE = 20
const LOW_THRESHOLD = 10

const rows = ref<InventoryVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const failed = ref('')
const keyword = ref('')
const skuIndex = reactive<Record<string, SkuView | undefined>>({})

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((r) => {
    const sku = skuIndex[r.skuId]
    return (
      r.skuId.includes(q) ||
      (sku?.skuCode || '').toLowerCase().includes(q) ||
      (sku?.title || '').toLowerCase().includes(q)
    )
  })
})

const lowStockCount = computed(
  () => rows.value.filter((r) => r.availableQty > 0 && r.availableQty < LOW_THRESHOLD).length
)
const outOfStockCount = computed(() => rows.value.filter((r) => r.availableQty <= 0).length)

async function fillSkuInfo(list: InventoryVO[]) {
  await Promise.allSettled(
    list.map(async (row) => {
      if (skuIndex[row.skuId] !== undefined) return
      try {
        skuIndex[row.skuId] = await getSku(row.skuId)
      } catch {
        skuIndex[row.skuId] = undefined
      }
    })
  )
}

async function load() {
  loading.value = true
  failed.value = ''
  try {
    const page = await listInventory(pageNo.value, PAGE_SIZE)
    rows.value = page.records
    total.value = page.total
    await fillSkuInfo(page.records)
  } catch (e) {
    failed.value = e instanceof Error ? e.message : '库存加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number) {
  pageNo.value = page
  load()
}

/* ------------------------------ 盘点调整 ------------------------------ */

const dialog = reactive({
  visible: false,
  skuId: '',
  skuLabel: '',
  adjustQty: 0,
  remark: '',
  /** 同一次提交重试必须复用；提交成功后换新 */
  idempotentKey: '',
  saving: false
})

function openAdjust(row: InventoryVO) {
  dialog.skuId = row.skuId
  const sku = skuIndex[row.skuId]
  dialog.skuLabel = sku ? `${sku.skuCode} · ${sku.title}` : `SKU ${row.skuId}`
  dialog.adjustQty = 0
  dialog.remark = ''
  dialog.idempotentKey = newIdempotentKey()
  dialog.visible = true
}

async function submitAdjust() {
  if (!dialog.adjustQty) {
    ElMessage.warning('调整数量不能为 0')
    return
  }
  const current = rows.value.find((r) => r.skuId === dialog.skuId)
  if (current && current.availableQty + dialog.adjustQty < 0) {
    ElMessage.warning('盘亏后可用库存不能为负')
    return
  }
  dialog.saving = true
  try {
    await adjustInventory({
      skuId: dialog.skuId,
      adjustQty: dialog.adjustQty,
      remark: dialog.remark.trim() || undefined,
      idempotentKey: dialog.idempotentKey
    })
    ElMessage.success('库存已调整')
    dialog.visible = false
    dialog.idempotentKey = newIdempotentKey()
    await load()
  } catch (e) {
    // 失败保留 idempotentKey：用户重试同一次调整不会重复记账
    ElMessage.error(e instanceof Error ? e.message : '调整失败')
  } finally {
    dialog.saving = false
  }
}

function statusOf(row: InventoryVO): 'ok' | 'low' | 'out' {
  if (row.availableQty <= 0) return 'out'
  if (row.availableQty < LOW_THRESHOLD) return 'low'
  return 'ok'
}

onMounted(async () => {
  await load()
  // 从商品页「查库存」跳进来时，直接定位并按 skuId 过滤
  const focus = String(route.query.skuId || '')
  if (focus) keyword.value = focus
})
</script>

<template>
  <div class="inventory">
    <header class="inventory__head">
      <div>
        <h2 class="inventory__title">库存管理</h2>
        <p class="inventory__desc">
          共 {{ total }} 个 SKU 有库存记录 · 可用 {{ rows.reduce((sum, r) => sum + r.availableQty, 0) }}
          · 仅剩少量 {{ lowStockCount }} · 缺货 {{ outOfStockCount }}
        </p>
      </div>

      <div class="inventory__actions">
        <el-input
          v-model="keyword"
          class="inventory__search"
          placeholder="按 SKU 编码 / 名称 / ID 过滤"
          :prefix-icon="Search"
          clearable
        />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <EmptyState v-if="failed" title="库存加载失败" :description="failed">
      <el-button @click="load">重新加载</el-button>
    </EmptyState>

    <template v-else>
      <div class="card table-card">
        <el-table v-loading="loading" :data="filtered" row-key="skuId">
          <el-table-column label="SKU" min-width="280">
            <template #default="{ row }">
              <div class="scell">
                <div class="scell__title">
                  {{ skuIndex[row.skuId]?.title || '—' }}
                </div>
                <div class="scell__meta">
                  <span class="mono">{{ skuIndex[row.skuId]?.skuCode || `ID ${row.skuId}` }}</span>
                  <span v-if="skuIndex[row.skuId]?.status === 'DISCONTINUED'" class="tag tag--off">已停售</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="单价" width="110">
            <template #default="{ row }">
              <span class="num">
                {{ skuIndex[row.skuId] ? `¥${Number(skuIndex[row.skuId]!.price).toFixed(2)}` : '—' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="可用" width="110" align="right">
            <template #default="{ row }">
              <span class="num qty" :class="`is-${statusOf(row)}`">{{ row.availableQty }}</span>
            </template>
          </el-table-column>

          <el-table-column label="预占" width="100" align="right">
            <template #default="{ row }">
              <span class="num">{{ row.reservedQty }}</span>
            </template>
          </el-table-column>

          <el-table-column label="合计" width="100" align="right">
            <template #default="{ row }">
              <span class="num">{{ row.totalQty }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <span class="badge" :class="`is-${statusOf(row)}`">
                {{ statusOf(row) === 'out' ? '缺货' : statusOf(row) === 'low' ? '仅剩少量' : '有货' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="110" align="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Sort" @click="openAdjust(row)">调整</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="total > PAGE_SIZE" class="pager">
        <el-pagination
          :current-page="pageNo"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="onPageChange"
        />
      </div>

      <p class="inventory__note">
        说明：可用 = 合计 − 预占；调整走 POST /v1/inventory/adjust，正数补货、负数盘亏，盘亏不得低于零。
        公开商城只暴露「有货 / 缺货」，精确数量仅商户员工可见。
      </p>
    </template>

    <el-dialog v-model="dialog.visible" title="盘点调整" width="440px">
      <div class="dlg">
        <div class="dlg__row">
          <span class="dlg__label">SKU</span>
          <span class="mono">{{ dialog.skuLabel }}</span>
        </div>
        <div class="dlg__row">
          <span class="dlg__label">调整数量</span>
          <el-input-number v-model="dialog.adjustQty" :step="1" :precision="0" />
          <span class="dlg__hint">正数补货 / 负数盘亏</span>
        </div>
        <div class="dlg__row">
          <span class="dlg__label">备注</span>
          <el-input v-model="dialog.remark" placeholder="如：供应商到货、盘点修正" maxlength="100" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submitAdjust">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.inventory__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.inventory__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.inventory__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.inventory__actions {
  display: flex;
  gap: 10px;
}

.inventory__search {
  width: 260px;
}

.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.table-card {
  padding: var(--space-2) var(--space-4) var(--space-3);
}

.scell__title {
  font-size: var(--fs-sm);
  color: var(--text-strong);
}

.scell__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--text-subtle);
}

.mono {
  font-family: var(--font-mono);
}

.tag {
  padding: 0 6px;
  border-radius: var(--radius-xs);

  &--off {
    background: var(--ink-100);
    color: var(--text-subtle);
  }
}

.qty {
  font-weight: 600;

  &.is-ok {
    color: var(--ink-900);
  }
  &.is-low {
    color: var(--amber-600);
  }
  &.is-out {
    color: var(--red-500, #d9534f);
  }
}

.badge {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;

  &.is-ok {
    background: var(--green-50);
    color: var(--green-500);
  }
  &.is-low {
    background: var(--amber-50);
    color: var(--amber-600);
  }
  &.is-out {
    background: var(--ink-100);
    color: var(--text-subtle);
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}

.inventory__note {
  margin-top: var(--space-3);
  font-size: 11px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.dlg__row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 0;
  font-size: var(--fs-sm);
  border-bottom: 1px solid var(--border-subtle);
}

.dlg__label {
  width: 76px;
  flex: none;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.dlg__hint {
  font-size: 11px;
  color: var(--text-subtle);
}

@media (max-width: 860px) {
  .inventory__head {
    flex-direction: column;
    align-items: flex-start;
  }
  .inventory__actions {
    width: 100%;
  }
  .inventory__search {
    flex: 1;
  }
}
</style>
