<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import { listSessions } from '@/api/chat'
import type { ChatSession } from '@/api/types'
import { relativeTime } from '@/utils/format'

const router = useRouter()
const sessions = ref<ChatSession[]>([])
const loading = ref(true)

const statusMeta: Record<string, { label: string; tone: string }> = {
  AGENT: { label: 'Agent 会话', tone: 'brand' },
  HUMAN: { label: '人工处理中', tone: 'warn' },
  CLOSED: { label: '已结束', tone: 'muted' }
}

onMounted(async () => {
  try {
    sessions.value = await listSessions('consumer')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="sessions">
    <header class="sessions__head">
      <div>
        <h2 class="sessions__title">历史会话</h2>
        <p class="sessions__desc">会话与消息保存在服务端，换设备登录也能接着看</p>
      </div>
    </header>

    <div v-if="loading" class="sessions__grid">
      <div v-for="i in 3" :key="i" class="skeleton" style="height: 150px" />
    </div>

    <EmptyState
      v-else-if="!sessions.length"
      title="还没有对话记录"
      description="去问问 Agent 吧，商品、库存、物流都能答"
    >
      <el-button type="primary" @click="router.push('/consumer/chat')">开始对话</el-button>
    </EmptyState>

    <div v-else class="sessions__grid">
      <article
        v-for="s in sessions"
        :key="s.id"
        class="scard"
        @click="router.push(`/consumer/chat/${s.id}`)"
      >
        <header class="scard__head">
          <span class="scard__status" :class="`is-${statusMeta[s.status].tone}`">
            {{ statusMeta[s.status].label }}
          </span>
          <span class="scard__time">{{ relativeTime(s.updatedAt) }}</span>
        </header>

        <h3 class="scard__title">{{ s.title }}</h3>
        <p class="scard__last clamp-2">{{ s.lastMessage || '暂无消息' }}</p>

        <footer class="scard__foot">
          <span class="scard__count num">{{ s.messageCount }} 条消息</span>
          <span class="scard__go">
            继续对话
            <el-icon :size="12"><ChatDotRound /></el-icon>
          </span>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped lang="scss">
.sessions__head {
  margin-bottom: var(--space-5);
}

.sessions__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.sessions__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.sessions__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
}

.scard {
  display: flex;
  flex-direction: column;
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--dur) var(--ease);

  &:hover {
    border-color: var(--brand-200);
    box-shadow: var(--shadow-sm);
    transform: translateY(-2px);
  }
}

.scard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.scard__status {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;

  &.is-brand {
    background: var(--brand-50);
    color: var(--brand-600);
  }
  &.is-warn {
    background: var(--amber-50);
    color: var(--amber-600);
  }
  &.is-muted {
    background: var(--ink-100);
    color: var(--text-subtle);
  }
}

.scard__time {
  font-size: 11px;
  color: var(--text-subtle);
}

.scard__title {
  margin-top: var(--space-3);
  font-size: var(--fs-base);
  font-weight: 500;
}

.scard__last {
  margin-top: 6px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-muted);
  min-height: 40px;
}

.scard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-subtle);
}

.scard__count {
  font-size: 11px;
  color: var(--text-subtle);
}

.scard__go {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-xs);
  color: var(--brand-600);
}
</style>
