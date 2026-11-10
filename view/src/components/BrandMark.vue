<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; size?: number }>(), { compact: false, size: 36 })
</script>

<template>
  <div class="brand">
    <svg
      :width="size"
      :height="size"
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="brand__mark"
    >
      <rect width="40" height="40" rx="11" fill="#3056D3" />
      <path d="M11 15.5h18l-2 9.5H13l-2-9.5Z" fill="#FFFFFF" fill-opacity="0.92" />
      <path d="M15 15.5a5 5 0 0 1 10 0" stroke="#FFFFFF" stroke-width="2.2" stroke-linecap="round" />
      <circle cx="27.5" cy="27" r="3.2" fill="#00A88F" stroke="#3056D3" stroke-width="1.6" />
    </svg>
    <div v-if="!compact" class="brand__text">
      <span class="brand__name">商户宝</span>
      <span class="brand__sub">3C 配件智能客服</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  user-select: none;
}

.brand__mark {
  flex: none;
  border-radius: 11px;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-strong);
  letter-spacing: -0.01em;
}

.brand__sub {
  font-size: 11px;
  color: var(--text-subtle);
  letter-spacing: 0.02em;
}
</style>
