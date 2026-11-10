<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
    size?: 'md' | 'lg'
  }>(),
  { title: '暂无数据', description: '', size: 'md' }
)
</script>

<template>
  <div class="empty" :class="`is-${size}`">
    <svg width="56" height="56" viewBox="0 0 56 56" fill="none" class="empty__art">
      <rect x="8" y="14" width="40" height="28" rx="7" stroke="#C2CEFC" stroke-width="2" />
      <path d="M8 24h40" stroke="#DFE6FF" stroke-width="2" />
      <circle cx="15" cy="19" r="1.8" fill="#93A9F7" />
      <circle cx="21" cy="19" r="1.8" fill="#C2CEFC" />
      <path d="M20 34h16" stroke="#DFE6FF" stroke-width="2" stroke-linecap="round" />
    </svg>
    <h3 class="empty__title">{{ title }}</h3>
    <p v-if="description" class="empty__desc">{{ description }}</p>
    <div v-if="$slots.default" class="empty__action">
      <slot />
    </div>
  </div>
</template>

<style scoped lang="scss">
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: var(--space-16) var(--space-6);
  text-align: center;
  background: var(--bg-surface);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-lg);

  &.is-lg {
    padding: var(--space-20) var(--space-6);
  }
}

.empty__art {
  opacity: 0.9;
}

.empty__title {
  margin-top: 4px;
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-body);
}

.empty__desc {
  max-width: 360px;
  font-size: var(--fs-sm);
  color: var(--text-subtle);
  line-height: var(--lh-loose);
}

.empty__action {
  margin-top: var(--space-2);
}
</style>
