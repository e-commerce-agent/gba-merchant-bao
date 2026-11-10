<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listPublicProducts } from '@/api/catalog'
import type { ProductSummary } from '@/api/types'

const route = useRoute()
const router = useRouter()

const categories = ['全部', '充电器', '数据线', '移动电源', '耳机', '车载充电', '无线充']
const sorts = [
  { label: '综合推荐', value: 'recommend' },
  { label: '价格从低到高', value: 'price-asc' },
  { label: '价格从高到低', value: 'price-desc' }
]

const keyword = ref(String(route.query.keyword || ''))
const category = ref(String(route.query.category || '全部'))
const sort = ref(String(route.query.sort || 'recommend'))
const page = ref(1)
const pageSize = 8

const list = ref<ProductSummary[]>([])
const total = ref(0)
const loading = ref(false)

const totalPages = computed(() => Math.ceil(total.value / pageSize))

async function fetchList() {
  loading.value = true
  try {
    const res = await listPublicProducts({
      keyword: keyword.value.trim() || undefined,
      category: category.value === '全部' ? undefined : category.value,
      sort: sort.value as 'recommend' | 'price-asc' | 'price-desc',
      page: page.value,
      pageSize
    })
    list.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function syncQuery() {
  router.replace({
    path: '/products',
    query: {
      ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
      ...(category.value !== '全部' ? { category: category.value } : {}),
      ...(sort.value !== 'recommend' ? { sort: sort.value } : {})
    }
  })
}

watch([keyword, category, sort], () => {
  page.value = 1
  syncQuery()
  fetchList()
})

watch(page, fetchList)

onMounted(fetchList)
</script>

<template>
  <div class="container list-page">
    <header class="list-head">
      <div>
        <h1 class="list-head__title">全部商品</h1>
        <p class="list-head__desc">共 {{ total }} 款在售商品 · 库存状态来自公开接口，不包含具体数量</p>
      </div>

      <el-input
        v-model="keyword"
        class="list-head__search"
        placeholder="搜索商品名、品牌、标签"
        :prefix-icon="Search"
        clearable
      />
    </header>

    <div class="filters">
      <div class="filters__cats">
        <button
          v-for="c in categories"
          :key="c"
          type="button"
          class="filters__cat"
          :class="{ 'is-active': category === c }"
          @click="category = c"
        >
          {{ c }}
        </button>
      </div>

      <el-select v-model="sort" class="filters__sort" size="default">
        <el-option v-for="s in sorts" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
    </div>

    <div v-if="loading" class="grid">
      <div v-for="i in pageSize" :key="i" class="skeleton" style="height: 320px" />
    </div>

    <EmptyState
      v-else-if="!list.length"
      title="没有找到匹配的商品"
      description="换个关键词或清空筛选条件再试一次"
    >
      <el-button @click="keyword = ''; category = '全部'">清空筛选</el-button>
    </EmptyState>

    <div v-else class="grid">
      <ProductCard v-for="p in list" :key="p.id" :product="p" />
    </div>

    <div v-if="totalPages > 1" class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        background
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.list-page {
  padding-top: var(--space-8);
  padding-bottom: var(--space-8);
}

.list-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-6);
  padding-bottom: var(--space-5);
}

.list-head__title {
  font-size: var(--fs-2xl);
  letter-spacing: -0.02em;
}

.list-head__desc {
  margin-top: 6px;
  font-size: var(--fs-sm);
  color: var(--text-muted);
}

.list-head__search {
  width: 300px;
}

.filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) 0 var(--space-5);
  border-top: 1px solid var(--border-subtle);
}

.filters__cats {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.filters__cat {
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  font-size: var(--fs-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);

  &:hover {
    background: var(--ink-100);
    color: var(--text-strong);
  }

  &.is-active {
    background: var(--ink-900);
    color: #fff;
    font-weight: 500;
  }
}

.filters__sort {
  width: 150px;
  flex: none;
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-5);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: var(--space-10);
}

@media (max-width: 1080px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 860px) {
  .list-head {
    flex-direction: column;
    align-items: flex-start;
  }
  .list-head__search {
    width: 100%;
  }
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .filters {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
