<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import { getOrderShipment } from '@/api/shipment'
import { ApiError } from '@/api/request'
import type { ShipmentStatus, ShipmentView } from '@/api/types'
import { formatDateTime } from '@/utils/format'

/**
 * 物流轨迹面板（shipment-v1）。
 * 消费者订单页与商户订单页共用：两边都只能按 orderId 查，权限由后端按主体判定。
 */
const props = defineProps<{ orderId: string }>()

const shipment = ref<ShipmentView | null>(null)
const loading = ref(false)
const error = ref('')

/** 物流状态：NOT_SHIPPED 是后端在“订单存在但无运单”时合成的，不是错误态 */
const statusMeta: Record<ShipmentStatus, { label: string; tone: string }> = {
  NOT_SHIPPED: { label: '待发货', tone: 'info' },
  IN_TRANSIT: { label: '运输中', tone: 'brand' },
  DELIVERED: { label: '已签收', tone: 'ok' }
}

const tracks = computed(() =>
  [...(shipment.value?.tracks ?? [])].sort(
    (a, b) => new Date(b.eventTime).getTime() - new Date(a.eventTime).getTime()
  )
)

async function load(orderId: string) {
  if (!orderId) return
  shipment.value = null
  error.value = ''
  loading.value = true
  try {
    shipment.value = await getOrderShipment(orderId)
  } catch (e) {
    // 404/10004 统一文案：不区分“没有这单”和“不是你的单”，避免泄露他人订单是否存在
    error.value =
      e instanceof ApiError && (e.status === 404 || e.code === 10004)
        ? '订单不存在或无权查看'
        : e instanceof ApiError
          ? e.message
          : '物流信息加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(() => props.orderId, load, { immediate: true })
</script>

<template>
  <div class="ship">
    <div v-if="loading">
      <div class="skeleton" style="height: 72px; margin-bottom: 16px" />
      <div class="skeleton" style="height: 140px" />
    </div>

    <EmptyState v-else-if="error" title="查不到物流信息" :description="error" />

    <template v-else-if="shipment">
      <div class="ship__head">
        <div class="ship__top">
          <span class="ship__carrier">
            {{ shipment.status === 'NOT_SHIPPED' ? '尚未发货' : shipment.carrierName || '承运商未回传' }}
          </span>
          <span class="ship__badge" :class="`is-${statusMeta[shipment.status].tone}`">
            {{ statusMeta[shipment.status].label }}
          </span>
        </div>

        <div v-if="shipment.trackingNo" class="ship__no num">
          运单号 {{ shipment.trackingNo }}
          <span v-if="shipment.carrierCode" class="ship__code">{{ shipment.carrierCode }}</span>
        </div>

        <div v-if="shipment.status === 'NOT_SHIPPED'" class="ship__hint">
          订单还没有运单。提交运单后这里会显示承运商、运单号与轨迹，通常 1–2 天内更新。
        </div>
        <div v-else class="ship__status">
          当前状态：{{ statusMeta[shipment.status].label }}
          <span v-if="shipment.status === 'IN_TRANSIT' && shipment.estimatedDeliveryAt" class="ship__eta">
            预计 {{ formatDateTime(shipment.estimatedDeliveryAt) }} 送达
          </span>
          <span v-else-if="shipment.deliveredAt" class="ship__eta">
            签收时间 {{ formatDateTime(shipment.deliveredAt) }}
          </span>
        </div>
      </div>

      <ul v-if="tracks.length" class="ship__timeline">
        <li
          v-for="(t, i) in tracks"
          :key="`${t.eventTime}-${i}`"
          class="ship__node"
          :class="{ 'is-latest': i === 0 }"
        >
          <span class="ship__dot" />
          <div class="ship__content">
            <div class="ship__tstatus">{{ t.description }}</div>
            <div v-if="t.location" class="ship__tdesc">{{ t.location }}</div>
            <div class="ship__ttime num">{{ formatDateTime(t.eventTime) }}</div>
          </div>
        </li>
      </ul>

      <div v-else class="ship__hint ship__hint--empty">暂无轨迹，承运商回传后会按时间倒序展示。</div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.ship__head {
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.ship__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.ship__carrier {
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-strong);
}

.ship__badge {
  padding: 2px 9px;
  border-radius: var(--radius-pill);
  font-size: 11px;

  &.is-brand {
    background: var(--brand-50);
    color: var(--brand-600);
  }
  &.is-ok {
    background: var(--green-50);
    color: var(--green-500);
  }
  &.is-info {
    background: var(--teal-50);
    color: var(--teal-600);
  }
}

.ship__no {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.ship__code {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--ink-100);
  color: var(--text-subtle);
}

.ship__status {
  margin-top: 8px;
  font-size: var(--fs-xs);
  color: var(--brand-600);
}

.ship__eta {
  margin-left: 6px;
  color: var(--text-muted);
}

.ship__hint {
  margin-top: 10px;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-muted);
}

.ship__hint--empty {
  margin-top: var(--space-5);
}

.ship__timeline {
  margin-top: var(--space-5);
  padding-left: 6px;
}

.ship__node {
  position: relative;
  display: flex;
  gap: var(--space-4);
  padding-bottom: var(--space-5);

  &::before {
    content: '';
    position: absolute;
    left: 4px;
    top: 12px;
    bottom: 0;
    width: 1px;
    background: var(--border-default);
  }

  &:last-child::before {
    display: none;
  }
}

.ship__dot {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--ink-200);
  flex: none;
}

.is-latest .ship__dot {
  background: var(--brand-500);
  box-shadow: 0 0 0 3px var(--brand-100);
}

.ship__tstatus {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-body);
}

.is-latest .ship__tstatus {
  color: var(--brand-600);
}

.ship__tdesc {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
  line-height: 1.7;
}

.ship__ttime {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-subtle);
}
</style>
