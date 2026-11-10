<script setup lang="ts">
import { computed } from 'vue'
import type { ProductThumbKey } from '@/api/types'

/**
 * 商品插画占位。
 * 不依赖外部图片资源：离线可跑、无版权问题，视觉上也不至于是一片灰块。
 * 后端接入真实图片后，把这里换成 <img :src="product.imageUrl"> 即可。
 */
const props = withDefaults(
  defineProps<{
    kind: ProductThumbKey
    size?: number
    ratio?: string
  }>(),
  { size: 160, ratio: '4 / 3' }
)

const tint = computed(() => {
  const map: Record<ProductThumbKey, string> = {
    charger: '#EEF2FF',
    cable: '#F1F5F9',
    powerbank: '#F0F7F5',
    earphone: '#F7F3FF',
    car: '#FFF7ED',
    wireless: '#F2F7FF',
    dock: '#F6F7F9'
  }
  return map[props.kind]
})
</script>

<template>
  <div class="thumb" :style="{ background: tint, aspectRatio: ratio }">
    <svg
      :width="size"
      :height="size"
      viewBox="0 0 160 160"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="thumb__svg"
    >
      <!-- 充电器 -->
      <g v-if="kind === 'charger'">
        <rect x="46" y="46" width="68" height="68" rx="16" fill="#2B3245" />
        <rect x="58" y="30" width="10" height="18" rx="3" fill="#8FA0BF" />
        <rect x="92" y="30" width="10" height="18" rx="3" fill="#8FA0BF" />
        <rect x="60" y="92" width="16" height="8" rx="4" fill="#5F7CEF" />
        <rect x="84" y="92" width="16" height="8" rx="4" fill="#DFE6FF" />
        <rect x="62" y="62" width="36" height="4" rx="2" fill="#4A5568" />
      </g>

      <!-- 数据线 -->
      <g v-else-if="kind === 'cable'">
        <path
          d="M52 108c0-26 18-20 18-36S58 50 58 38"
          stroke="#2B3245"
          stroke-width="10"
          stroke-linecap="round"
        />
        <path
          d="M108 52c0 26-18 20-18 36s12 22 12 34"
          stroke="#8FA0BF"
          stroke-width="10"
          stroke-linecap="round"
        />
        <rect x="48" y="113" width="20" height="14" rx="6" fill="#3056D3" />
        <rect x="98" y="33" width="20" height="14" rx="6" fill="#3056D3" />
        <rect x="70" y="72" width="20" height="4" rx="2" fill="#DFE6FF" />
      </g>

      <!-- 充电宝 -->
      <g v-else-if="kind === 'powerbank'">
        <rect x="50" y="40" width="60" height="80" rx="14" fill="#2B3245" />
        <circle cx="80" cy="76" r="18" stroke="#00A88F" stroke-width="3" fill="none" />
        <circle cx="80" cy="76" r="8" fill="#00A88F" opacity="0.35" />
        <rect x="66" y="105" width="28" height="4" rx="2" fill="#4A5568" />
        <circle cx="80" cy="52" r="3" fill="#00A88F" />
      </g>

      <!-- 耳机 -->
      <g v-else-if="kind === 'earphone'">
        <rect x="46" y="74" width="68" height="46" rx="18" fill="#2B3245" opacity="0.9" />
        <circle cx="62" cy="60" r="13" fill="#2B3245" />
        <circle cx="98" cy="60" r="13" fill="#2B3245" />
        <rect x="58" y="72" width="8" height="18" rx="4" fill="#8FA0BF" />
        <rect x="94" y="72" width="8" height="18" rx="4" fill="#8FA0BF" />
        <rect x="72" y="92" width="16" height="3" rx="1.5" fill="#00A88F" />
      </g>

      <!-- 车充 -->
      <g v-else-if="kind === 'car'">
        <rect x="46" y="54" width="68" height="52" rx="14" fill="#2B3245" />
        <circle cx="90" cy="80" r="6" fill="#F79009" />
        <circle cx="108" cy="80" r="6" fill="#F79009" opacity="0.5" />
        <rect x="56" y="34" width="14" height="22" rx="5" fill="#8FA0BF" />
        <rect x="80" y="34" width="14" height="22" rx="5" fill="#8FA0BF" />
        <rect x="58" y="68" width="24" height="4" rx="2" fill="#4A5568" />
      </g>

      <!-- 磁吸无线充 -->
      <g v-else-if="kind === 'wireless'">
        <circle cx="80" cy="70" r="30" fill="#2B3245" />
        <circle cx="80" cy="70" r="18" stroke="#5F7CEF" stroke-width="3" fill="none" />
        <path d="M50 100h60" stroke="#2B3245" stroke-width="8" stroke-linecap="round" />
        <path d="M80 100v22" stroke="#8FA0BF" stroke-width="6" stroke-linecap="round" />
        <path d="M62 122h36" stroke="#2B3245" stroke-width="8" stroke-linecap="round" />
      </g>

      <!-- 桌面充电站 -->
      <g v-else-if="kind === 'dock'">
        <rect x="40" y="86" width="80" height="30" rx="10" fill="#2B3245" />
        <rect x="52" y="94" width="10" height="6" rx="3" fill="#5F7CEF" />
        <rect x="66" y="94" width="10" height="6" rx="3" fill="#5F7CEF" />
        <rect x="80" y="94" width="10" height="6" rx="3" fill="#DFE6FF" />
        <rect x="94" y="94" width="10" height="6" rx="3" fill="#DFE6FF" />
        <rect x="66" y="52" width="28" height="34" rx="6" fill="#8FA0BF" />
        <rect x="72" y="60" width="16" height="3" rx="1.5" fill="#F1F5F9" />
      </g>
    </svg>
  </div>
</template>

<style scoped lang="scss">
.thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  overflow: hidden;
  border-radius: var(--radius-md);
}

.thumb__svg {
  width: 100%;
  height: 100%;
  max-width: 78%;
  max-height: 78%;
}
</style>
