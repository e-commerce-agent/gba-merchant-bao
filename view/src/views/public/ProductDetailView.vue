<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ChatDotRound, CircleCheck, Van } from '@element-plus/icons-vue'
import ProductThumb from '@/components/ProductThumb.vue'
import StockBadge from '@/components/StockBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getPublicProduct } from '@/api/catalog'
import type { ProductDetail, Sku } from '@/api/types'
import { formatMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const product = ref<ProductDetail | null>(null)
const loading = ref(true)
const selected = ref<Record<string, string>>({})
const activeTab = ref<'detail' | 'specs' | 'compat' | 'after'>('detail')

/** 把 SKU 的扁平行属性聚成「颜色 / 插头规格」这样的可选组 */
const groups = computed(() => {
  if (!product.value) return [] as Array<{ name: string; values: string[] }>
  const map = new Map<string, string[]>()
  product.value.skus.forEach((s) =>
    s.attributes.forEach((a) => {
      const arr = map.get(a.name) || []
      if (!arr.includes(a.value)) arr.push(a.value)
      map.set(a.name, arr)
    })
  )
  return Array.from(map.entries()).map(([name, values]) => ({ name, values }))
})

const currentSku = computed<Sku | undefined>(() => {
  if (!product.value) return undefined
  return product.value.skus.find((s) => s.attributes.every((a) => selected.value[a.name] === a.value))
})

const displayPrice = computed(() => {
  if (!product.value) return ''
  if (currentSku.value) return formatMoney(currentSku.value.price, currentSku.value.currency)
  return `${formatMoney(product.value.minPrice)} 起`
})

const displayStock = computed(() => currentSku.value?.stockStatus || product.value?.stockStatus || 'IN_STOCK')

function valueAvailable(name: string, value: string): boolean {
  if (!product.value) return false
  return product.value.skus.some(
    (s) =>
      s.attributes.some((a) => a.name === name && a.value === value) &&
      s.stockStatus !== 'OUT_OF_STOCK'
  )
}

function pick(name: string, value: string) {
  selected.value = { ...selected.value, [name]: value }
}

function askAgent() {
  router.push({
    path: '/consumer/chat',
    query: { productId: product.value?.id, skuId: currentSku.value?.id }
  })
}

onMounted(async () => {
  try {
    const detail = await getPublicProduct(String(route.params.id))
    product.value = detail
    selected.value = Object.fromEntries(detail.skus[0].attributes.map((a) => [a.name, a.value]))
  } catch {
    product.value = null
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container detail">
    <div v-if="loading" class="detail__skeleton">
      <div class="skeleton" style="height: 420px" />
      <div class="skeleton" style="height: 420px" />
    </div>

    <EmptyState
      v-else-if="!product"
      size="lg"
      title="商品不存在或已下架"
      description="商品可能已停售，返回全部商品看看其他型号"
    >
      <el-button type="primary" @click="router.push('/products')">返回商品列表</el-button>
    </EmptyState>

    <template v-else>
      <nav class="crumb">
        <RouterLink to="/">首页</RouterLink>
        <span>/</span>
        <RouterLink to="/products">全部商品</RouterLink>
        <span>/</span>
        <span class="crumb__current">{{ product.name }}</span>
      </nav>

      <div class="detail__grid">
        <!-- 左侧图片 -->
        <div class="gallery">
          <div class="gallery__main">
            <ProductThumb :kind="product.thumb" :size="360" ratio="1 / 1" />
          </div>
          <div class="gallery__thumbs">
            <div v-for="i in 3" :key="i" class="gallery__thumb-item" :class="{ 'is-active': i === 1 }">
              <ProductThumb :kind="product.thumb" :size="60" ratio="1 / 1" />
            </div>
          </div>
        </div>

        <!-- 右侧信息 -->
        <div class="info">
          <div class="info__tags">
            <span class="info__cat">{{ product.categoryName }}</span>
            <span v-for="tag in product.tags" :key="tag" class="info__tag">{{ tag }}</span>
          </div>

          <h1 class="info__title">{{ product.name }}</h1>
          <p class="info__sub">{{ product.subtitle }}</p>

          <div class="info__price-row">
            <div class="info__price">{{ displayPrice }}</div>
            <StockBadge :status="displayStock" />
            <span class="info__code num">{{ currentSku?.skuCode || product.model }}</span>
          </div>

          <div class="info__block">
            <div v-for="g in groups" :key="g.name" class="sku-row">
              <span class="sku-row__label">{{ g.name }}</span>
              <div class="sku-row__values">
                <button
                  v-for="v in g.values"
                  :key="v"
                  type="button"
                  class="sku-opt"
                  :class="{
                    'is-active': selected[g.name] === v,
                    'is-disabled': !valueAvailable(g.name, v)
                  }"
                  @click="pick(g.name, v)"
                >
                  {{ v }}
                </button>
              </div>
            </div>
          </div>

          <div class="info__actions">
            <el-button type="primary" size="large" class="info__ask" @click="askAgent">
              <el-icon :size="16"><ChatDotRound /></el-icon>
              问客服 Agent
            </el-button>
            <el-button size="large" @click="router.push('/products')">继续逛</el-button>
          </div>

          <ul class="info__services">
            <li>
              <el-icon :size="14"><CircleCheck /></el-icon>
              7 天无理由退换 · 18 个月只换不修
            </li>
            <li>
              <el-icon :size="14"><Van /></el-icon>
              广东次日达 · 跨省 2–3 天，运费以订单为准
            </li>
          </ul>

          <div class="info__hint">
            <span class="info__hint-label">兼容性存疑？</span>
            把手机型号或笔记本型号告诉 Agent，会按协议和接口帮你判断，并附上来源说明。
            <button class="info__hint-link" @click="askAgent">
              开始咨询
              <el-icon :size="11"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </div>

      <!-- 详情页签 -->
      <section class="tabs">
        <div class="tabs__bar">
          <button
            v-for="tab in [
              { key: 'detail', label: '商品详情' },
              { key: 'specs', label: '规格参数' },
              { key: 'compat', label: '兼容性' },
              { key: 'after', label: '售后政策' }
            ]"
            :key="tab.key"
            type="button"
            class="tabs__item"
            :class="{ 'is-active': activeTab === tab.key }"
            @click="activeTab = tab.key as typeof activeTab"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="tabs__panel">
          <template v-if="activeTab === 'detail'">
            <p class="para">{{ product.description }}</p>
            <ul class="points">
              <li v-for="p in product.sellingPoints" :key="p">
                <i />
                {{ p }}
              </li>
            </ul>
          </template>

          <table v-else-if="activeTab === 'specs'" class="spec-table">
            <tbody>
              <tr v-for="s in product.specs" :key="s.label">
                <th>{{ s.label }}</th>
                <td>{{ s.value }}</td>
              </tr>
            </tbody>
          </table>

          <ul v-else-if="activeTab === 'compat'" class="points">
            <li v-for="c in product.compatibility" :key="c">
              <i />
              {{ c }}
            </li>
          </ul>

          <p v-else class="para">{{ product.afterSale }}</p>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.detail {
  padding-top: var(--space-5);
  padding-bottom: var(--space-10);
}

.detail__skeleton {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-8);
  margin-top: var(--space-6);
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
  padding: var(--space-3) 0 var(--space-5);

  a:hover {
    color: var(--brand-600);
  }
}

.crumb__current {
  color: var(--text-muted);
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail__grid {
  display: grid;
  grid-template-columns: 480px 1fr;
  gap: var(--space-10);
  align-items: start;
}

/* 左侧 */
.gallery {
  position: sticky;
  top: calc(var(--header-h) + var(--space-5));
}

.gallery__main {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--bg-surface);
}

.gallery__thumbs {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.gallery__thumb-item {
  width: 68px;
  padding: 4px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  cursor: pointer;

  &.is-active {
    border-color: var(--brand-400);
    box-shadow: 0 0 0 2px var(--brand-50);
  }
}

/* 右侧 */
.info {
  padding-top: var(--space-2);
}

.info__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.info__cat {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: var(--fs-xs);
  font-weight: 500;
}

.info__tag {
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--ink-50);
  color: var(--text-muted);
  font-size: var(--fs-xs);
}

.info__title {
  margin-top: var(--space-4);
  font-size: var(--fs-2xl);
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.info__sub {
  margin-top: 8px;
  font-size: var(--fs-sm);
  color: var(--text-muted);
}

.info__price-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-md);
  background: var(--ink-50);
}

.info__price {
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.info__code {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.info__block {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-top: var(--space-6);
}

.sku-row {
  display: flex;
  gap: var(--space-4);
}

.sku-row__label {
  width: 72px;
  flex: none;
  padding-top: 7px;
  font-size: var(--fs-sm);
  color: var(--text-muted);
}

.sku-row__values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sku-opt {
  min-width: 68px;
  padding: 6px 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  font-size: var(--fs-sm);
  color: var(--text-body);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);

  &:hover {
    border-color: var(--brand-300);
    color: var(--brand-600);
  }

  &.is-active {
    border-color: var(--brand-500);
    background: var(--brand-50);
    color: var(--brand-600);
    font-weight: 500;
  }

  &.is-disabled {
    border-style: dashed;
    color: var(--text-subtle);
    background: var(--ink-50);
    cursor: not-allowed;
  }
}

.info__actions {
  display: flex;
  gap: 10px;
  margin-top: var(--space-6);
}

.info__ask {
  min-width: 180px;

  .el-icon {
    margin-right: 6px;
  }
}

.info__services {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: var(--space-5);
  padding-top: var(--space-5);
  border-top: 1px solid var(--border-subtle);

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: var(--fs-xs);
    color: var(--text-muted);
  }

  .el-icon {
    color: var(--green-500);
  }
}

.info__hint {
  margin-top: var(--space-5);
  padding: var(--space-4);
  border: 1px dashed var(--brand-200);
  border-radius: var(--radius-md);
  background: var(--brand-50);
  font-size: var(--fs-xs);
  line-height: var(--lh-loose);
  color: var(--text-body);
}

.info__hint-label {
  font-weight: 500;
  color: var(--brand-600);
  margin-right: 4px;
}

.info__hint-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: 4px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--brand-600);
  font-size: var(--fs-xs);
  font-weight: 500;
  cursor: pointer;
}

/* 页签 */
.tabs {
  margin-top: var(--space-16);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.tabs__bar {
  display: flex;
  gap: 2px;
  padding: 0 var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.tabs__item {
  position: relative;
  padding: 14px 12px;
  border: none;
  background: transparent;
  font-size: var(--fs-sm);
  color: var(--text-muted);
  cursor: pointer;

  &:hover {
    color: var(--text-strong);
  }

  &.is-active {
    color: var(--brand-600);
    font-weight: 500;

    &::after {
      content: '';
      position: absolute;
      left: 10px;
      right: 10px;
      bottom: -1px;
      height: 2px;
      border-radius: 2px;
      background: var(--brand-500);
    }
  }
}

.tabs__panel {
  padding: var(--space-6);
}

.para {
  max-width: 780px;
  font-size: var(--fs-sm);
  line-height: 1.9;
  color: var(--text-body);
}

.points {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: var(--space-4);

  li {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    font-size: var(--fs-sm);
    color: var(--text-body);
    line-height: var(--lh-loose);
  }

  i {
    width: 6px;
    height: 6px;
    margin-top: 8px;
    border-radius: 50%;
    background: var(--brand-300);
    flex: none;
  }
}

.spec-table {
  width: 100%;
  max-width: 720px;
  border-collapse: collapse;
  font-size: var(--fs-sm);

  tr + tr {
    border-top: 1px solid var(--border-subtle);
  }

  th {
    width: 160px;
    padding: 12px 16px 12px 0;
    text-align: left;
    font-weight: 400;
    color: var(--text-muted);
    background: transparent;
    vertical-align: top;
  }

  td {
    padding: 12px 0;
    color: var(--text-strong);
  }
}

@media (max-width: 1024px) {
  .detail__grid {
    grid-template-columns: 1fr;
  }
  .gallery {
    position: static;
    max-width: 420px;
  }
}

@media (max-width: 640px) {
  .details__grid {
    grid-template-columns: 1fr;
  }
  .info__title {
    font-size: var(--fs-xl);
  }
  .info__actions {
    flex-direction: column;

    .el-button {
      width: 100%;
      margin-left: 0;
    }
  }
}
</style>
