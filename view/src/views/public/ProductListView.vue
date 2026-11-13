<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listPublicProducts } from '@/api/catalog'
import { toCardModel } from '@/api/adapters'
import type { ProductSummary } from '@/api/types'

const route = useRoute()
const router = useRouter()

/**
 * 公开商品列表（product-v1）。
 * 后端是游标分页（cursor + size，没有 total），也不支持关键词与排序参数，
 * 所以搜索只在「已加载的商品」里做前端过滤，翻页用「加载更多」而不是页码。
 */
const PAGE_SIZE = 12

const keyword = ref(String(route.query.keyword || ''))
const items = ref<ProductSummary[]>([])
const cursor = ref<string | undefined>()
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref('')

const filtered = computed(() => {
  const q = keyword.value.trim()
  if (!q) return items.value
  return items.value.filter(
    (p) =>
      p.name.includes(q) ||
      (p.brand || '').includes(q) ||
      (p.model || '').includes(q) ||
      p.tags.some((t) => t.includes(q))
  )
})

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
    const page = await listPublicProducts({ size: PAGE_SIZE, cursor: cursor.value })
    const mapped = page.items.map(toCardModel)
    items.value = reset ? mapped : [...items.value, ...mapped]
    cursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore && page.nextCursor)
  } catch (e) {
    failed.value = e instanceof Error ? e.message : '商品加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

watch(keyword, (value) => {
  router.replace({ path: '/products', query: value.trim() ? { keyword: value.trim() } : {} })
})

onMounted(() => load(true))
</script>

<template>
  <div class="container list-page">
    <header class="list-head">
      <div>
        <h1 class="list-head__title">全部商品</h1>
        <p class="list-head__desc">
          已加载 {{ items.length }} 款在售商品<template v-if="hasMore">，还有更多</template>
          · 库存只展示「有货 / 缺货」，不含具体数量
        </p>
      </div>

      <el-input
        v-model="keyword"
        class="list-head__search"
        placeholder="在已加载商品中搜索名称、品牌、属性"
        :prefix-icon="Search"
        clearable
      />
    </header>

    <div v-if="loading" class="grid">
      <div v-for="i in 8" :key="i" class="skeleton" style="height: 320px" />
    </div>

    <EmptyState
      v-else-if="failed"
      title="商品加载失败"
      :description="failed"
    >
      <el-button @click="load(true)">重新加载</el-button>
    </EmptyState>

    <EmptyState
      v-else-if="!filtered.length"
      title="没有找到匹配的商品"
      :description="keyword ? '试试别的关键词，或清空搜索后翻页加载更多' : '店铺还没有上架商品'"
    >
      <el-button v-if="keyword" @click="keyword = ''">清空搜索</el-button>
    </EmptyState>

    <template v-else>
      <div class="grid">
        <ProductCard v-for="p in filtered" :key="p.id" :product="p" />
      </div>

      <div v-if="hasMore" class="more">
        <el-button :loading="loadingMore" @click="load(false)">
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </el-button>
      </div>
    </template>
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
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: var(--space-6);
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
  width: 320px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-5);
}

.more {
  display: flex;
  justify-content: center;
  margin-top: var(--space-8);
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
}
</style>
