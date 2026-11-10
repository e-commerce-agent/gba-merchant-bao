<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ArrowRight, WarnTriangleFilled } from '@element-plus/icons-vue'
import { getMerchantOverview, listDocumentTasks } from '@/api/knowledge'
import { listSessions } from '@/api/chat'
import type { ChatSession, DocumentTask, MerchantOverview } from '@/api/types'
import { relativeTime } from '@/utils/format'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const overview = ref<MerchantOverview | null>(null)
const sessions = ref<ChatSession[]>([])
const tasks = ref<DocumentTask[]>([])
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const kpis = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    {
      label: '本周会话',
      value: String(o.sessionTotal),
      unit: '次',
      delta: `+${o.sessionDelta}%`,
      note: '较上周同期'
    },
    {
      label: '自动解决率',
      value: `${Math.round(o.autoResolvedRate * 100)}`,
      unit: '%',
      delta: 'Agent 直答',
      note: `${o.takeoverCount} 次转人工`
    },
    {
      label: '平均首次响应',
      value: String(o.avgFirstResponseSec),
      unit: 's',
      delta: '含工具调用',
      note: '人工平均 42s'
    },
    {
      label: '待处理',
      value: String(o.lowConfidenceSessions),
      unit: '个',
      delta: '低置信度',
      note: `${o.pendingTasks} 个文档任务进行中`
    }
  ]
})

const takeoverSessions = computed(() =>
  sessions.value.filter((s) => s.status === 'HUMAN' || (s.confidence ?? 1) < 0.7)
)

const runningTasks = computed(() =>
  tasks.value.filter((t) => t.status !== 'SUCCEEDED' && t.status !== 'FAILED')
)

function renderChart() {
  if (!chartRef.value || !overview.value) return
  chart = echarts.init(chartRef.value)
  const trend = overview.value.trend

  chart.setOption({
    grid: { left: 8, right: 8, top: 24, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0F1729',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: '#DFE3EA' } }
    },
    legend: {
      right: 0,
      top: 0,
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { color: '#667085', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date),
      axisLine: { lineStyle: { color: '#ECEEF4' } },
      axisTick: { show: false },
      axisLabel: { color: '#8B94A7', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#F1F3F7' } },
      axisLabel: { color: '#8B94A7', fontSize: 11 }
    },
    series: [
      {
        name: '会话量',
        type: 'bar',
        data: trend.map((t) => t.sessions),
        barWidth: 14,
        itemStyle: { color: '#3056D3', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '转人工',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.takeovers),
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#00A88F', width: 2 },
        itemStyle: { color: '#00A88F' }
      }
    ]
  })
}

function resize() {
  chart?.resize()
}

onMounted(async () => {
  const [o, s, t] = await Promise.all([
    getMerchantOverview(),
    listSessions('merchant'),
    listDocumentTasks()
  ])
  overview.value = o
  sessions.value = s
  tasks.value = t
  renderChart()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<template>
  <div class="dash">
    <header class="dash__head">
      <div>
        <h2 class="dash__title">今天的会话情况</h2>
        <p class="dash__desc">数据在 demo 阶段为样例值，接入真实接口后由 /v1/merchant/overview 提供</p>
      </div>
      <el-button @click="router.push('/merchant/conversations')">
        进入会话工作台
        <el-icon :size="12" style="margin-left: 4px"><ArrowRight /></el-icon>
      </el-button>
    </header>

    <div class="kpis">
      <div v-for="k in kpis" :key="k.label" class="kpi">
        <div class="kpi__label">{{ k.label }}</div>
        <div class="kpi__value num">
          {{ k.value }}<span>{{ k.unit }}</span>
        </div>
        <div class="kpi__foot">
          <span class="kpi__delta">{{ k.delta }}</span>
          <span class="kpi__note">{{ k.note }}</span>
        </div>
      </div>
    </div>

    <div class="dash__row">
      <section class="card chart-card">
        <header class="card__head">
          <h3>近 7 天会话趋势</h3>
          <span class="card__hint">柱：会话量 · 线：转人工</span>
        </header>
        <div ref="chartRef" class="chart" />
      </section>

      <section class="card side-card">
        <header class="card__head">
          <h3>待接管会话</h3>
          <span class="side-card__count num">{{ takeoverSessions.length }}</span>
        </header>

        <ul class="takeover">
          <li
            v-for="s in takeoverSessions.slice(0, 4)"
            :key="s.id"
            class="takeover__item"
            @click="router.push('/merchant/conversations')"
          >
            <div class="takeover__top">
              <span class="takeover__name">{{ s.consumerName || '消费者' }}</span>
              <span class="takeover__time">{{ relativeTime(s.updatedAt) }}</span>
            </div>
            <div class="takeover__title ellipsis">{{ s.title }}</div>
            <div class="takeover__meta">
              <span v-if="s.status === 'HUMAN'" class="takeover__tag is-human">人工中</span>
              <span v-else class="takeover__tag is-low">
                <el-icon :size="10"><WarnTriangleFilled /></el-icon>
                置信度 {{ Math.round((s.confidence ?? 0) * 100) }}%
              </span>
              <span class="takeover__msgs num">{{ s.messageCount }} 条</span>
            </div>
          </li>
        </ul>

        <button class="side-card__more" type="button" @click="router.push('/merchant/conversations')">
          查看全部会话
        </button>
      </section>
    </div>

    <div class="dash__row dash__row--bottom">
      <section class="card tasks-card">
        <header class="card__head">
          <h3>文档处理任务</h3>
          <button class="card__link" type="button" @click="router.push('/merchant/tasks')">
            全部任务
          </button>
        </header>

        <div v-if="!runningTasks.length" class="tasks-empty">当前没有进行中的任务</div>

        <ul v-else class="tasks">
          <li v-for="t in runningTasks" :key="t.id" class="task">
            <div class="task__info">
              <span class="task__name ellipsis">{{ t.fileName }}</span>
              <span class="task__status">{{ t.status }}</span>
            </div>
            <el-progress
              :percentage="t.progress"
              :show-text="false"
              :stroke-width="6"
              color="#3056D3"
            />
          </li>
        </ul>
      </section>

      <section class="card tips-card">
        <header class="card__head">
          <h3>本周建议</h3>
        </header>
        <ul class="tips">
          <li>
            <span class="tips__no">01</span>
            「港版插头区别」近 7 天被问 23 次，建议补一条知识库 FAQ，可减少重复转人工。
          </li>
          <li>
            <span class="tips__no">02</span>
            有 3 个会话因库存信息过期触发了低置信度，建议重新导入库存对照表。
          </li>
          <li>
            <span class="tips__no">03</span>
            文档任务 DT3004 解析失败，多为合并单元格导致，换 CSV 重新上传即可。
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dash__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.dash__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.dash__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}

.kpi {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.kpi__label {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.kpi__value {
  margin-top: 8px;
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.02em;
  line-height: 1.1;

  span {
    margin-left: 3px;
    font-size: var(--fs-sm);
    font-weight: 500;
    color: var(--text-muted);
  }
}

.kpi__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.kpi__delta {
  padding: 1px 7px;
  border-radius: var(--radius-xs);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 500;
}

.kpi__note {
  font-size: 11px;
  color: var(--text-subtle);
}

.dash__row {
  display: grid;
  grid-template-columns: 1.65fr 1fr;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.card {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);

  h3 {
    font-size: var(--fs-base);
    font-weight: 500;
  }
}

.card__hint {
  font-size: 11px;
  color: var(--text-subtle);
}

.card__link {
  border: none;
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--brand-600);
  cursor: pointer;
}

.chart {
  height: 248px;
  width: 100%;
}

.side-card__count {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--amber-50);
  color: var(--amber-600);
  font-size: 11px;
  font-weight: 500;
}

.takeover {
  display: flex;
  flex-direction: column;
}

.takeover__item {
  padding: var(--space-3) 0;
  border-top: 1px solid var(--border-subtle);
  cursor: pointer;

  &:hover .takeover__title {
    color: var(--brand-600);
  }
}

.takeover__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.takeover__name {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-strong);
}

.takeover__time {
  font-size: 11px;
  color: var(--text-subtle);
}

.takeover__title {
  margin-top: 3px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.takeover__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.takeover__tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  font-size: 10.5px;
  font-weight: 500;

  &.is-human {
    background: var(--teal-50);
    color: var(--teal-600);
  }

  &.is-low {
    background: var(--amber-50);
    color: var(--amber-600);
  }
}

.takeover__msgs {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.side-card__more {
  width: 100%;
  margin-top: var(--space-3);
  padding: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  font-size: var(--fs-xs);
  color: var(--text-body);
  cursor: pointer;

  &:hover {
    border-color: var(--brand-200);
    color: var(--brand-600);
  }
}

.dash__row--bottom {
  grid-template-columns: 1fr 1fr;
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-top: var(--space-2);
}

.task__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: 6px;
}

.task__name {
  font-size: var(--fs-xs);
  color: var(--text-body);
}

.task__status {
  font-size: 10.5px;
  color: var(--brand-600);
  font-family: var(--font-mono);
}

.tasks-empty {
  padding: var(--space-6) 0;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.tips {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);

  li {
    display: flex;
    gap: 10px;
    font-size: var(--fs-xs);
    line-height: 1.8;
    color: var(--text-muted);
  }
}

.tips__no {
  flex: none;
  font-family: var(--font-mono);
  color: var(--brand-400);
}

@media (max-width: 1100px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }
  .dash__row,
  .dash__row--bottom {
    grid-template-columns: 1fr;
  }
}
</style>
