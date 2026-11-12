<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import ProductThumb from '@/components/ProductThumb.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  getPublicProduct,
  listCategories,
  listProducts,
  publishProduct,
  unpublishProduct,
  updateSku
} from '@/api/catalog'
import type { CategoryView, ProductStatus, ProductView, PublicProductView } from '@/api/types'
import { thumbOf } from '@/utils/thumb'
import { formatDateTime, formatMoney } from '@/utils/format'
import { toNumber } from '@/api/adapters'

const router = useRouter()

/**
 * 商品与 SKU（商户端）。
 * 读：GET /v1/products（游标分页，员工可见全部状态）+ GET /v1/product-categories
 * 写：POST /v1/products/{id}/publish|unpublish、PUT /v1/skus/{skuId}
 * SKU 明细：契约未提供员工端「按商品列 SKU」接口，已发布商品复用公开详情
 * （GET /v1/stores/{slug}/products/{id}，含 ACTIVE SKU），未发布商品提示先发布。
 */
const PAGE_SIZE = 20

const items = ref<ProductView[]>([])
const cursor = ref<string | undefined>()
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const failed = ref('')
const keyword = ref('')
const activeStatus = ref<'ALL' | ProductStatus>('ALL')

const categories = ref<CategoryView[]>([])
const categoryNameOf = computed(() => {
  const map = new Map<string, string>()
  const walk = (list: CategoryView[]) => {
    list.forEach((c) => {
      map.set(c.categoryId, c.name)
      if (c.children?.length) walk(c.children)
    })
  }
  walk(categories.value)
  return map
})

/** 已发布商品的公开视图（含 SKU），按 productId 懒加载缓存 */
const publicIndex = reactive<Record<string, PublicProductView | undefined>>({})
const skuLoading = reactive<Record<string, boolean>>({})
const expandedIds = ref<string[]>([])

const statusMeta: Record<ProductStatus, { label: string; tone: string }> = {
  DRAFT: { label: '草稿', tone: 'muted' },
  PUBLISHED: { label: '已上架', tone: 'on' },
  UNPUBLISHED: { label: '已下架', tone: 'off' },
  DISCONTINUED: { label: '已停售', tone: 'muted' }
}

const filtered = computed(() => {
  const q = keyword.value.trim()
  return items.value.filter((p) => {
    const statusOk = activeStatus.value === 'ALL' || p.status === activeStatus.value
    const keywordOk =
      !q ||
      p.name.includes(q) ||
      p.productCode.includes(q) ||
      (p.brand || '').includes(q)
    return statusOk && keywordOk
  })
})

const publishedCount = computed(() => items.value.filter((p) => p.status === 'PUBLISHED').length)

async function load(reset = false) {
  if (reset) {
    loading.value = true
    failed.value = ''
    cursor.value = undefined
    items.value = []
  } else {
    loadingMore.value = true
  }
  try {
    const page = await listProducts({ size: PAGE_SIZE, cursor: cursor.value })
    items.value = reset ? page.items : [...items.value, ...page.items]
    cursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore && page.nextCursor)
  } catch (e) {
    failed.value = e instanceof Error ? e.message : '商品加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await listCategories()
  } catch {
    // 类目失败不影响列表展示，表格里退回显示 categoryId
    categories.value = []
  }
}

async function onExpandChange(row: ProductView, rows: ProductView[]) {
  expandedIds.value = rows.map((r) => r.productId)
  const isExpanded = rows.some((r) => r.productId === row.productId)
  if (!isExpanded || publicIndex[row.productId] !== undefined) return
  if (row.status !== 'PUBLISHED') {
    publicIndex[row.productId] = undefined
    return
  }
  skuLoading[row.productId] = true
  try {
    publicIndex[row.productId] = await getPublicProduct(row.productId)
  } catch {
    publicIndex[row.productId] = undefined
  } finally {
    skuLoading[row.productId] = false
  }
}

async function togglePublish(row: ProductView) {
  const publishing = row.status !== 'PUBLISHED'
  try {
    await ElMessageBox.confirm(
      publishing
        ? `上架后「${row.name}」会出现在公开商城，未上架前公开接口查不到它。`
        : `下架后「${row.name}」在公开商城立即不可见，已下订单不受影响。`,
      publishing ? '确认上架' : '确认下架',
      { confirmButtonText: publishing ? '上架' : '下架', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const updated = publishing
      ? await publishProduct(row.productId)
      : await unpublishProduct(row.productId)
    const idx = items.value.findIndex((p) => p.productId === row.productId)
    if (idx >= 0) items.value[idx] = updated
    // 状态变了，公开 SKU 缓存失效
    delete publicIndex[row.productId]
    if (expandedIds.value.includes(row.productId)) {
      await onExpandChange(updated, [updated])
    }
    ElMessage.success(publishing ? '已上架' : '已下架')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

/* ------------------------------ SKU 改价 ------------------------------ */

const priceDialog = reactive({
  visible: false,
  skuId: '',
  skuCode: '',
  title: '',
  price: 0,
  saving: false
})

function openPriceDialog(sku: { skuId: string; skuCode: string; title: string; price: string }) {
  priceDialog.skuId = sku.skuId
  priceDialog.skuCode = sku.skuCode
  priceDialog.title = sku.title
  priceDialog.price = toNumber(sku.price)
  priceDialog.visible = true
}

async function savePrice() {
  if (!(priceDialog.price > 0)) {
    ElMessage.warning('价格必须大于 0')
    return
  }
  priceDialog.saving = true
  try {
    await updateSku(priceDialog.skuId, { price: priceDialog.price })
    ElMessage.success('价格已更新')
    priceDialog.visible = false
    await reloadExpanded()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '改价失败')
  } finally {
    priceDialog.saving = false
  }
}

async function reloadExpanded() {
  const ids = [...expandedIds.value]
  for (const id of ids) {
    const row = items.value.find((p) => p.productId === id)
    if (!row || row.status !== 'PUBLISHED') continue
    try {
      publicIndex[id] = await getPublicProduct(id)
    } catch {
      delete publicIndex[id]
    }
  }
}

onMounted(async () => {
  await Promise.all([load(true), loadCategories()])
})
</script>

<template>
  <div class="products">
    <header class="products__head">
      <div>
        <h2 class="products__title">商品与 SKU</h2>
        <p class="products__desc">
          已加载 {{ items.length }} 个 SPU · 在售 {{ publishedCount }}
          <template v-if="hasMore">· 还有更多</template>
          · 价格与库存挂在 SKU 上，库存请到「库存管理」调整
        </p>
      </div>

      <div class="products__actions">
        <el-input
          v-model="keyword"
          class="products__search"
          placeholder="搜索商品名、编码、品牌"
          :prefix-icon="Search"
          clearable
        />
        <el-button :icon="Refresh" :loading="loading" @click="load(true)">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="ElMessage.info('新建商品表单待商品负责人确认字段后接入')">
          新建商品
        </el-button>
      </div>
    </header>

    <div class="status-bar">
      <button
        v-for="s in [
          { key: 'ALL', label: '全部' },
          { key: 'PUBLISHED', label: '已上架' },
          { key: 'UNPUBLISHED', label: '已下架' },
          { key: 'DRAFT', label: '草稿' },
          { key: 'DISCONTINUED', label: '已停售' }
        ]"
        :key="s.key"
        type="button"
        class="status-bar__tab"
        :class="{ 'is-active': activeStatus === s.key }"
        @click="activeStatus = s.key as typeof activeStatus"
      >
        {{ s.label }}
        <span class="num">
          {{ s.key === 'ALL' ? items.length : items.filter((p) => p.status === s.key).length }}
        </span>
      </button>
    </div>

    <EmptyState
      v-if="failed"
      title="商品加载失败"
      :description="failed"
    >
      <el-button @click="load(true)">重新加载</el-button>
    </EmptyState>

    <div v-else class="card table-card">
      <el-table
        v-loading="loading"
        :data="filtered"
        row-key="productId"
        :expand-row-keys="expandedIds"
        @expand-change="onExpandChange"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <div v-if="row.status !== 'PUBLISHED'" class="expand__loading">
                该商品当前为「{{ statusMeta[row.status as ProductStatus].label }}」，公开接口只返回已上架商品的 SKU。
                上架后可在此查看 SKU 明细与改价。
              </div>

              <div v-else-if="skuLoading[row.productId]" class="expand__loading">加载 SKU…</div>

              <div v-else-if="!publicIndex[row.productId]" class="expand__loading">
                未能取到该商品的 SKU，稍后展开重试。
              </div>

              <table v-else class="sku-table">
                <thead>
                  <tr>
                    <th>SKU 编码</th>
                    <th>规格</th>
                    <th>属性</th>
                    <th>价格</th>
                    <th>可售</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in publicIndex[row.productId]!.skus" :key="s.skuId">
                    <td class="num mono">{{ s.skuCode }}</td>
                    <td>{{ s.title }}</td>
                    <td class="attrs">
                      <span v-for="(v, k) in s.attributes" :key="k" class="attrs__item">{{ k }}：{{ v }}</span>
                    </td>
                    <td class="num">{{ formatMoney(toNumber(s.price)) }}</td>
                    <td>
                      <span :class="s.inStock ? 'is-stock' : 'is-out'">{{ s.inStock ? '有货' : '缺货' }}</span>
                    </td>
                    <td class="right">
                      <el-button link type="primary" @click="openPriceDialog(s)">改价</el-button>
                      <el-button
                        link
                        type="primary"
                        @click="router.push({ path: '/merchant/inventory', query: { skuId: s.skuId } })"
                      >
                        查库存
                      </el-button>
                    </td>
                  </tr>
                  <tr v-if="!publicIndex[row.productId]!.skus.length">
                    <td colspan="6" class="empty-cell">该商品还没有 ACTIVE SKU</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="商品" min-width="280">
          <template #default="{ row }">
            <div class="pcell">
              <div class="pcell__thumb">
                <ProductThumb :kind="thumbOf(row.name)" :size="44" ratio="1 / 1" />
              </div>
              <div class="pcell__info">
                <div class="pcell__name">{{ row.name }}</div>
                <div class="pcell__meta">
                  <span class="pcell__model mono">{{ row.productCode }}</span>
                  <span v-if="row.brand" class="pcell__brand">{{ row.brand }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类目" width="130">
          <template #default="{ row }">
            <span class="cat">
              {{ categoryNameOf.get(row.categoryId) || row.categoryId || '—' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status" :class="`is-${statusMeta[row.status as ProductStatus].tone}`">
              {{ statusMeta[row.status as ProductStatus].label }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">
            <span class="num time">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status === 'DISCONTINUED'"
              @click="togglePublish(row)"
            >
              {{ row.status === 'PUBLISHED' ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="hasMore" class="more">
        <el-button :loading="loadingMore" @click="load(false)">
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </el-button>
      </div>
    </div>

    <p class="products__note">
      说明：读接口为 GET /v1/products（游标分页）；上架/下架调用 POST /v1/products/{id}/publish|unpublish；
      SKU 改价调用 PUT /v1/skus/{skuId}。员工端暂未提供「按商品列 SKU」接口，已上架商品的 SKU 明细复用公开详情。
    </p>

    <el-dialog v-model="priceDialog.visible" title="调整 SKU 价格" width="420px">
      <div class="dlg">
        <div class="dlg__row">
          <span class="dlg__label">SKU</span>
          <span class="mono">{{ priceDialog.skuCode }}</span>
        </div>
        <div class="dlg__row">
          <span class="dlg__label">规格</span>
          <span>{{ priceDialog.title }}</span>
        </div>
        <div class="dlg__row">
          <span class="dlg__label">价格</span>
          <el-input-number v-model="priceDialog.price" :min="0.01" :precision="2" :step="1" />
        </div>
      </div>
      <template #footer>
        <el-button @click="priceDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="priceDialog.saving" @click="savePrice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.products__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
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

.status-bar {
  display: flex;
  gap: 4px;
  margin-bottom: var(--space-4);
}

.status-bar__tab {
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

.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.table-card {
  padding: var(--space-2) var(--space-4) var(--space-3);
}

.more {
  display: flex;
  justify-content: center;
  padding: var(--space-4) 0;
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

.pcell__brand {
  font-size: 10.5px;
  color: var(--brand-600);
  background: var(--brand-50);
  padding: 0 6px;
  border-radius: var(--radius-xs);
}

.cat {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.time {
  font-size: 11px;
  color: var(--text-subtle);
}

.status {
  font-size: var(--fs-xs);

  &.is-on {
    color: var(--green-500);
  }

  &.is-off {
    color: var(--amber-600);
  }

  &.is-muted {
    color: var(--text-subtle);
  }
}

.mono {
  font-family: var(--font-mono);
}

.expand {
  padding: var(--space-3) var(--space-6) var(--space-4) 48px;
  background: var(--bg-subtle);
}

.expand__loading {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
  line-height: 1.7;
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

  .right {
    text-align: right;
    white-space: nowrap;
  }

  .is-stock {
    color: var(--green-500);
  }

  .is-out {
    color: var(--amber-600);
  }
}

.attrs {
  max-width: 220px;

  &__item {
    display: inline-block;
    margin-right: 8px;
    color: var(--text-subtle);
  }
}

.empty-cell {
  text-align: center;
  color: var(--text-subtle);
}

.products__note {
  margin-top: var(--space-3);
  font-size: 11px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.dlg__row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 8px 0;
  font-size: var(--fs-sm);
  border-bottom: 1px solid var(--border-subtle);
}

.dlg__label {
  width: 64px;
  flex: none;
  font-size: var(--fs-xs);
  color: var(--text-muted);
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
