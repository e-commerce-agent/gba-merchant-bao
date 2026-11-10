<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { ProductSummary } from '@/api/types'
import ProductThumb from './ProductThumb.vue'
import StockBadge from './StockBadge.vue'

const props = defineProps<{ product: ProductSummary }>()
const router = useRouter()

const priceText = computed(() =>
  props.product.minPrice === props.product.maxPrice
    ? String(props.product.minPrice)
    : `${props.product.minPrice} – ${props.product.maxPrice}`
)

function open() {
  router.push(`/products/${props.product.id}`)
}
</script>

<template>
  <article class="pcard" @click="open">
    <div class="pcard__media">
      <ProductThumb :kind="product.thumb" />
      <span class="pcard__cat">{{ product.categoryName }}</span>
    </div>

    <div class="pcard__body">
      <h3 class="pcard__name clamp-2">{{ product.name }}</h3>
      <p class="pcard__sub ellipsis">{{ product.subtitle }}</p>

      <div class="pcard__tags">
        <span v-for="tag in product.tags.slice(0, 2)" :key="tag" class="pcard__tag">{{ tag }}</span>
      </div>

      <div class="pcard__foot">
        <div class="pcard__price">
          <span class="num">{{ priceText }}</span>
          <span class="pcard__unit">起</span>
        </div>
        <StockBadge :status="product.stockStatus" />
      </div>
    </div>
  </article>
</template>

<style scoped lang="scss">
.pcard {
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition:
    transform var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease),
    border-color var(--dur) var(--ease);

  &:hover {
    transform: translateY(-3px);
    border-color: var(--border-default);
    box-shadow: var(--shadow-md);

    .pcard__name {
      color: var(--brand-600);
    }
  }
}

.pcard__media {
  position: relative;
  padding: var(--space-3);
  padding-bottom: 0;
}

.pcard__cat {
  position: absolute;
  top: 18px;
  left: 18px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(6px);
  font-size: 11px;
  color: var(--text-muted);
}

.pcard__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--space-4);
  flex: 1;
}

.pcard__name {
  font-size: var(--fs-base);
  font-weight: 500;
  line-height: 1.45;
  color: var(--text-strong);
  transition: color var(--dur-fast) var(--ease);
  min-height: 40px;
}

.pcard__sub {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.pcard__tags {
  display: flex;
  gap: 6px;
  margin-top: 2px;
}

.pcard__tag {
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--ink-50);
  color: var(--text-muted);
  font-size: 11px;
}

.pcard__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-3);
}

.pcard__price {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
  font-variant-numeric: tabular-nums;

  &::before {
    content: '¥';
    font-size: var(--fs-sm);
    font-weight: 500;
    color: var(--ink-600);
  }
}

.pcard__unit {
  font-size: var(--fs-xs);
  font-weight: 400;
  color: var(--text-subtle);
}
</style>
