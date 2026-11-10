<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import ProductThumb from '@/components/ProductThumb.vue'
import StockBadge from '@/components/StockBadge.vue'
import { listProducts, getProduct } from '@/api/catalog'
import type { ProductDetail, ProductSummary, Sku } from '@/api/types'
import { formatMoney } from '@/utils/format'

const keyword = ref('')
const list = ref<ProductSummary[]>([])
const loading = ref(true)
const total = ref(0)
const skuCache = ref<Record<string, Sku[]>>({})
const detailCache = ref<Record<string, ProductDetail>>({})
const editing = ref<ProductDetail | null>(null)
const drawerVisible = ref(false)

const onSaleCount = computed(() => list.value.filter((p) => p.status === 'ON_SALE').length)
const lowStockCount = computed(
  () => list.value.filter((p) => p.stockStatus === 'LOW_STOCK' || p.stockStatus === 'OUT_OF_STOCK').length
)

async function fetchList() {
  loading.value = true
  try {
    const res = await listProducts({ keyword: keyword.value.trim() || undefined, page: 1, pageSize: 20 })
    list.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function onExpand(row: ProductSummary, expandedRows: ProductSummary[]) {
  const isExpanded = expandedRows.some((r) => r.id === row.id)
  if (!isExpanded || skuCache.value[row.id]) return
  const detail = await getProduct(row.id)
  detailCache.value[row.id] = detail
  skuCache.value[row.id] = detail.skus
}

function openEdit(row: ProductSummary) {
  const detail = detailCache.value[row.id]
  if (!detail) {
    ElMessage.info('SKU 明细正在加载，请先展开该商品')
    return
  }
  editing.value = detail
  drawerVisible.value = true
}

onMounted(fetchList)
</script>

<template>
  <div class="products">
    <header class="products__head">
      <div>
        <h2 class="products__title">商品与 SKU</h2>
        <p class="products__desc">
          共 {{ total }} 个 SPU · 在售 {{ onSaleCount }} · 库存异常 {{ lowStockCount }}
        </p>
      </div>

      <div class="products__actions">
        <el-input
          v-model="keyword"
          class="products__search"
          placeholder="搜索商品名称"
          :prefix-icon="Search"
          clearable
          @keyup.enter="fetchList"
          @clear="fetchList"
        />
        <el-button type="primary" :icon="Plus" @click="ElMessage.info('写接口待后端交付：POST /v1/products')">
          新建商品
        </el-button>
      </div>
    </header>

    <div class="card table-card">
      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        :expand-row-keys="[]"
        @expand-change="onExpand"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <div v-if="!skuCache[row.id]" class="expand__loading">加载 SKU…</div>

              <table v-else class="sku-table">
                <thead>
                  <tr>
                    <th>SKU 编码</th>
                    <th>规格</th>
                    <th>价格</th>
                    <th>库存</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in skuCache[row.id]" :key="s.id">
                    <td class="num mono">{{ s.skuCode }}</td>
                    <td>{{ s.name }}</td>
                    <td class="num">{{ formatMoney(s.price, s.currency) }}</td>
                    <td class="num">
                      <span :class="{ 'is-low': (s.stockQty ?? 0) < 15 }">{{ s.stockQty ?? '—' }}</span>
                    </td>
                    <td>
                      <StockBadge :status="s.stockStatus" compact />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="商品" min-width="300">
          <template #default="{ row }">
            <div class="pcell">
              <div class="pcell__thumb">
                <ProductThumb :kind="row.thumb" :size="44" ratio="1 / 1" />
              </div>
              <div class="pcell__info">
                <div class="pcell__name">{{ row.name }}</div>
                <div class="pcell__meta">
                  <span class="pcell__model mono">{{ row.model }}</span>
                  <span class="pcell__cat">{{ row.categoryName }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="价格" width="130">
          <template #default="{ row }">
            <span class="num strong">
              {{ row.minPrice === row.maxPrice ? formatMoney(row.minPrice) : `${formatMoney(row.minPrice)} 起` }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="SKU" width="80">
          <template #default="{ row }">
            <span class="num">{{ row.skuCount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="库存状态" width="120">
          <template #default="{ row }">
            <StockBadge :status="row.stockStatus" />
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status" :class="row.status === 'ON_SALE' ? 'is-on' : 'is-off'">
              {{ row.status === 'ON_SALE' ? '在售' : '已下架' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <p class="products__note">
      提示：展开行会懒加载该商品的 SKU 明细（对应 GET /v1/products/{id}）。写操作接口待后端交付。
    </p>

    <el-drawer v-model="drawerVisible" :title="editing?.name || '商品详情'" size="520px">
      <div v-if="editing" class="edit">
        <div class="edit__row">
          <span class="edit__label">SPU 编码</span>
          <span class="mono">{{ editing.model }}</span>
        </div>
        <div class="edit__row">
          <span class="edit__label">类目</span>
          <span>{{ editing.categoryName }}</span>
        </div>
        <div class="edit__row">
          <span class="edit__label">售卖状态</span>
          <span>{{ editing.status === 'ON_SALE' ? '在售' : '已下架' }}</span>
        </div>

        <h4 class="edit__section">SKU 明细</h4>
        <table class="sku-table">
          <thead>
            <tr>
              <th>规格</th>
              <th>价格</th>
              <th>库存</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in editing.skus" :key="s.id">
              <td>{{ s.name }}</td>
              <td class="num">{{ formatMoney(s.price, s.currency) }}</td>
              <td class="num">{{ s.stockQty ?? '—' }}</td>
            </tr>
          </tbody>
        </table>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="编辑表单待接口"
          description="PUT /v1/products/{id} 与 SKU 库存调整接口由后端交付后接入，当前仅展示只读明细。"
          class="edit__alert"
        />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.products__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.products__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.products__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.products__actions {
  display: flex;
  gap: 10px;
}

.products__search {
  width: 240px;
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

.pcell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pcell__thumb {
  width: 44px;
  flex: none;
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.pcell__name {
  font-size: var(--fs-sm);
  color: var(--text-strong);
}

.pcell__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.pcell__model {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.pcell__cat {
  font-size: 10.5px;
  color: var(--brand-600);
  background: var(--brand-50);
  padding: 0 6px;
  border-radius: var(--radius-xs);
}

.mono {
  font-family: var(--font-mono);
}

.status {
  font-size: var(--fs-xs);

  &.is-on {
    color: var(--green-500);
  }

  &.is-off {
    color: var(--text-subtle);
  }
}

.expand {
  padding: var(--space-3) var(--space-6) var(--space-4) 48px;
  background: var(--bg-subtle);
}

.expand__loading {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.sku-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-xs);

  th {
    padding: 6px 10px;
    text-align: left;
    font-weight: 500;
    color: var(--text-muted);
    background: transparent;
    border-bottom: 1px solid var(--border-subtle);
  }

  td {
    padding: 8px 10px;
    color: var(--text-body);
    border-bottom: 1px dashed var(--border-subtle);
  }

  tr:last-child td {
    border-bottom: none;
  }

  .is-low {
    color: var(--amber-600);
    font-weight: 500;
  }
}

.products__note {
  margin-top: var(--space-3);
  font-size: 11px;
  color: var(--text-subtle);
}

.edit__row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 8px 0;
  font-size: var(--fs-sm);
  border-bottom: 1px solid var(--border-subtle);
}

.edit__label {
  width: 80px;
  flex: none;
  color: var(--text-muted);
  font-size: var(--fs-xs);
}

.edit__section {
  margin: var(--space-5) 0 var(--space-3);
  font-size: var(--fs-sm);
  font-weight: 500;
}

.edit__alert {
  margin-top: var(--space-5);
}

@media (max-width: 860px) {
  .products__head {
    flex-direction: column;
    align-items: flex-start;
  }
  .products__actions {
    width: 100%;
  }
  .products__search {
    flex: 1;
  }
}
</style>
