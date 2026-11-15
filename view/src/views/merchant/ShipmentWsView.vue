<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Position } from '@element-plus/icons-vue'
import { pingShipmentService, queryShipmentByOrderNo, SoapFaultError, SHIPMENT_WS_ENDPOINT } from '@/api/shipmentWs'
import type { ShipmentWsSummary } from '@/api/shipmentWs'
import { useAuthStore } from '@/stores/auth'

/**
 * 商户端 WebService 物流查询：直连后端 SOAP 1.1 服务（shipment.wsdl）。
 * 展示 SOAP 集成能力：ping 连通探测 + queryShipmentByOrderNo 运单查询，
 * 与 REST（GET /orders/{orderId}/shipment）并存，供外部系统对接。
 */

const auth = useAuthStore()

const merchantCode = ref(auth.user?.merchantCode || 'M_3C_DEMO')
const orderNo = ref('')
const pinging = ref(false)
const pingOk = ref<boolean | null>(null)
const pingText = ref('')
const querying = ref(false)
const summary = ref<ShipmentWsSummary | null>(null)

async function ping() {
  pinging.value = true
  pingOk.value = null
  try {
    pingText.value = await pingShipmentService()
    pingOk.value = true
  } catch (e) {
    pingOk.value = false
    pingText.value = e instanceof SoapFaultError ? e.message : '连接失败'
  } finally {
    pinging.value = false
  }
}

async function query() {
  const code = merchantCode.value.trim()
  const order = orderNo.value.trim()
  if (!code) {
    ElMessage.warning('请填写商户编码')
    return
  }
  if (!order) {
    ElMessage.warning('请填写订单号')
    return
  }
  querying.value = true
  summary.value = null
  try {
    summary.value = await queryShipmentByOrderNo(code, order)
  } catch (e) {
    if (e instanceof SoapFaultError) ElMessage.error(`SOAP Fault：${e.message}`)
    else ElMessage.error('查询失败，请稍后重试')
  } finally {
    querying.value = false
  }
}

onMounted(ping)
</script>

<template>
  <div class="ws">
    <header class="ws__head">
      <div>
        <h2 class="ws__title">WebService 物流查询</h2>
        <p class="ws__desc">
          直连后端 SOAP 1.1 服务（shipment.wsdl，{{ SHIPMENT_WS_ENDPOINT }}），
          面向外部 ERP / TMS 系统的对接演示；浏览器内以 fetch 直发 SOAP 信封并解析 XML
        </p>
      </div>
      <span class="ws__ping" :class="pingOk === true ? 'is-ok' : pingOk === false ? 'is-down' : ''">
        <i />
        {{ pingOk === true ? '服务在线' : pingOk === false ? '服务不可达' : '检测中…' }}
      </span>
    </header>

    <section class="panel form">
      <div class="form__row">
        <label>商户编码</label>
        <el-input v-model="merchantCode" maxlength="40" placeholder="M_3C_DEMO" />
      </div>
      <div class="form__row">
        <label>订单号</label>
        <el-input
          v-model="orderNo"
          maxlength="40"
          placeholder="例如 DEV-2026-0001"
          @keydown.enter="query"
        />
      </div>
      <div class="form__actions">
        <el-button :loading="pinging" @click="ping">ping 连通测试</el-button>
        <el-button type="primary" :icon="Position" :loading="querying" @click="query">
          queryShipmentByOrderNo
        </el-button>
        <span v-if="pingText && pingOk" class="form__ping-text num">{{ pingText }}</span>
      </div>
    </section>

    <section v-if="summary" class="panel result">
      <header class="result__head">
        <h3>运单摘要 · ShipmentSummary</h3>
        <span class="result__status">{{ summary.status }}</span>
      </header>

      <dl class="meta">
        <div><dt>订单号</dt><dd class="num">{{ summary.orderNo || '—' }}</dd></div>
        <div><dt>承运商</dt><dd>{{ summary.carrierName || '—' }}（{{ summary.carrierCode || '—' }}）</dd></div>
        <div><dt>运单号</dt><dd class="num">{{ summary.trackingNo || '—' }}</dd></div>
        <div><dt>最近事件</dt><dd class="num">{{ summary.lastEventTime || '—' }}</dd></div>
      </dl>

      <h4 v-if="summary.track.length" class="result__sub">物流轨迹（{{ summary.track.length }} 条）</h4>
      <ul v-if="summary.track.length" class="timeline">
        <li v-for="(t, i) in summary.track" :key="i" class="timeline__item">
          <i />
          <div>
            <p class="timeline__desc">{{ t.description }}</p>
            <p class="timeline__meta num">
              {{ t.eventTime }}<template v-if="t.location"> · {{ t.location }}</template>
            </p>
          </div>
        </li>
      </ul>
      <p v-else class="result__empty">该订单尚未产生物流轨迹（NOT_SHIPPED）</p>
    </section>

    <section v-else class="panel tip">
      <el-icon :size="18" color="#98A2B3"><Connection /></el-icon>
      <p>输入订单号查询运单摘要与轨迹；未发货的订单会返回 NOT_SHIPPED 空轨迹视图</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.ws__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.ws__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.ws__desc {
  margin-top: 6px;
  max-width: 680px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-subtle);
}

.ws__ping {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  background: var(--bg-subtle, #f2f4f7);
  color: var(--text-muted);

  i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #98a2b3;
  }

  &.is-ok {
    background: var(--teal-50, #e6f7f3);
    color: var(--teal-600, #00a88f);

    i {
      background: var(--teal-500, #00a88f);
    }
  }

  &.is-down {
    background: var(--red-50, #fdecec);
    color: var(--red-500, #e5484d);

    i {
      background: var(--red-500, #e5484d);
    }
  }
}

.panel {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.form__row {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: var(--space-3);

  label {
    font-size: var(--fs-xs);
    color: var(--text-muted);
  }
}

.form__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-1, 4px);
}

.form__ping-text {
  font-size: 11px;
  color: var(--text-subtle);
}

.result {
  margin-top: var(--space-4);
}

.result__head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    font-size: var(--fs-base);
    font-weight: 500;
  }
}

.result__status {
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-mono);
}

.meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3) var(--space-6);
  margin: var(--space-4) 0;

  > div {
    display: flex;
    gap: var(--space-2);
  }

  dt {
    flex: none;
    font-size: var(--fs-xs);
    color: var(--text-subtle);
  }

  dd {
    margin: 0;
    font-size: var(--fs-sm);
    color: var(--text-body);
  }
}

.result__sub {
  margin-top: var(--space-4);
  font-size: var(--fs-sm);
  font-weight: 500;
}

.timeline {
  margin-top: var(--space-3);
}

.timeline__item {
  position: relative;
  display: flex;
  gap: var(--space-3);
  padding: 0 0 var(--space-4) var(--space-5);

  &::before {
    content: '';
    position: absolute;
    left: 5px;
    top: 12px;
    bottom: -2px;
    width: 1px;
    background: var(--border-subtle);
  }

  &:last-child::before {
    display: none;
  }

  i {
    position: absolute;
    left: 0;
    top: 5px;
    width: 11px;
    height: 11px;
    border-radius: 50%;
    background: var(--brand-500, #3056d3);
    border: 2px solid var(--brand-50, #e8eeff);
  }
}

.timeline__desc {
  font-size: var(--fs-sm);
  color: var(--text-body);
}

.timeline__meta {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-subtle);
}

.result__empty {
  margin-top: var(--space-3);
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.tip {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-4);

  p {
    font-size: var(--fs-xs);
    color: var(--text-subtle);
  }
}

@media (max-width: 720px) {
  .meta {
    grid-template-columns: 1fr;
  }
}
</style>
