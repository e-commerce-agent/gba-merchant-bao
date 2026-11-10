<script setup lang="ts">
import { computed } from 'vue'
import type { StockStatus } from '@/api/types'

const props = withDefaults(defineProps<{ status: StockStatus; compact?: boolean }>(), {
  compact: false
})

const meta = computed(() => {
  const map: Record<StockStatus, { label: string; tone: string }> = {
    IN_STOCK: { label: '有货', tone: 'ok' },
    LOW_STOCK: { label: '仅剩少量', tone: 'warn' },
    OUT_OF_STOCK: { label: '暂时缺货', tone: 'off' }
  }
  return map[props.status]
})
</script>

<template>
  <span class="stock" :class="[`is-${meta.tone}`, { 'is-compact': compact }]">
    <i class="stock__dot" />
    {{ meta.label }}
  </span>
</template>

<style scoped lang="scss">
.stock {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-xs);
  font-weight: 500;
  line-height: 18px;

  &.is-compact {
    padding: 0;
    background: transparent;
  }
}

.stock__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.is-ok {
  color: var(--green-500);
  background: var(--green-50);
}

.is-warn {
  color: var(--amber-600);
  background: var(--amber-50);
}

.is-off {
  color: var(--text-subtle);
  background: var(--ink-100);

  .stock__dot {
    background: var(--ink-300);
  }
}
</style>
