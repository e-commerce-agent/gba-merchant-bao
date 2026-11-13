<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Search } from '@element-plus/icons-vue'
import ChatBubble from '@/components/ChatBubble.vue'
import EmptyState from '@/components/EmptyState.vue'
import { archiveSession, listMessages, listSessions, sendMessage } from '@/api/chat'
import type { ChatMessage, ChatSession } from '@/api/types'
import { relativeTime } from '@/utils/format'

/**
 * 契约（agent-conversation-v1）本期没有人工接管与置信度：
 * 会话状态只有 ACTIVE/ARCHIVED，消息发送统一触发 Agent run（SSE 流式）。
 * 商户端定位为「查看对话 + 与 Agent 交互 + 归档」，接管能力待后端扩展后接入。
 */
const sessions = ref<ChatSession[]>([])
const messages = ref<ChatMessage[]>([])
const activeId = ref('')
const keyword = ref('')
const filter = ref<'ALL' | 'ACTIVE' | 'ARCHIVED'>('ALL')
const loading = ref(true)
const input = ref('')
const sending = ref(false)
const scroller = ref<HTMLElement>()

const activeSession = computed(() => sessions.value.find((s) => s.id === activeId.value))

const filtered = computed(() =>
  sessions.value.filter((s) => {
    const kw = keyword.value.trim()
    if (kw && !s.title.includes(kw)) return false
    if (filter.value === 'ALL') return true
    // 展示模型状态（AGENT/CLOSED）与筛选键（ACTIVE/ARCHIVED）一一对应
    return filter.value === 'ARCHIVED' ? s.status === 'CLOSED' : s.status === 'AGENT'
  })
)

async function select(id: string) {
  activeId.value = id
  messages.value = await listMessages(id)
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

async function archive() {
  if (!activeSession.value) return
  try {
    await archiveSession(activeId.value)
    const idx = sessions.value.findIndex((s) => s.id === activeId.value)
    if (idx >= 0) sessions.value[idx] = { ...sessions.value[idx], status: 'CLOSED' }
    ElMessage.success('会话已归档')
  } catch (e) {
    // 运行中的会话归档会被 409/13002 busy 拒绝
    ElMessage.error((e as Error).message || '归档失败，请稍后重试')
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value || !activeId.value) return
  sending.value = true
  input.value = ''

  const localMsg: ChatMessage = {
    id: `local-${Date.now()}`,
    sessionId: activeId.value,
    role: 'user',
    content: text,
    createdAt: new Date().toISOString()
  }
  messages.value.push(localMsg)

  const scroll = () => {
    void nextTick().then(() => {
      if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
    })
  }
  scroll()

  try {
    await sendMessage(activeId.value, text, {
      onStart(bubble) {
        messages.value.push(bubble)
        scroll()
      },
      onDelta(bubble) {
        const idx = messages.value.findIndex((m) => m.id === bubble.id)
        if (idx >= 0) messages.value[idx] = bubble
        scroll()
      },
      onDone(bubble) {
        const idx = messages.value.findIndex((m) => m.id === bubble.id)
        if (idx >= 0) messages.value[idx] = bubble
        const si = sessions.value.findIndex((s) => s.id === activeId.value)
        if (si >= 0) sessions.value[si] = { ...sessions.value[si], updatedAt: new Date().toISOString() }
        scroll()
      },
      onError(error) {
        ElMessage.error(error.message || '发送失败')
      }
    })
  } catch {
    /* 错误已在 onError 提示 */
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  try {
    sessions.value = await listSessions({ size: 50, audience: 'merchant' })
    if (sessions.value.length) await select(sessions.value[0].id)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="conv">
    <!-- 左：会话列表 -->
    <aside class="conv__list">
      <div class="conv__search">
        <el-input v-model="keyword" placeholder="搜索会话或消费者" :prefix-icon="Search" clearable />
      </div>

      <div class="conv__filters">
        <button
          v-for="f in [
            { key: 'ALL', label: '全部' },
            { key: 'ACTIVE', label: '进行中' },
            { key: 'ARCHIVED', label: '已归档' }
          ]"
          :key="f.key"
          type="button"
          class="conv__filter"
          :class="{ 'is-active': filter === f.key }"
          @click="filter = f.key as typeof filter"
        >
          {{ f.label }}
        </button>
      </div>

      <div v-if="loading" class="conv__sessions">
        <div v-for="i in 4" :key="i" class="skeleton" style="height: 76px; margin-bottom: 8px" />
      </div>

      <div v-else class="conv__sessions">
        <button
          v-for="s in filtered"
          :key="s.id"
          type="button"
          class="sitem"
          :class="{ 'is-active': s.id === activeId }"
          @click="select(s.id)"
        >
          <div class="sitem__top">
            <span class="sitem__name">{{ s.consumerName || '消费者' }}</span>
            <span class="sitem__time">{{ relativeTime(s.updatedAt) }}</span>
          </div>
          <div class="sitem__title ellipsis">{{ s.title }}</div>
          <div class="sitem__meta">
            <span class="sitem__dot" :class="`is-${s.status.toLowerCase()}`" />
            <span class="sitem__status">
              {{ s.status === 'CLOSED' ? '已归档' : '进行中' }}
            </span>
            <span v-if="s.unread" class="sitem__unread num">{{ s.unread }}</span>
          </div>
        </button>

        <div v-if="!filtered.length" class="conv__empty">没有符合条件的会话</div>
      </div>
    </aside>

    <!-- 右：会话详情 -->
    <section class="conv__detail">
      <template v-if="activeSession">
        <header class="conv__head">
          <div class="conv__head-left">
            <h2 class="conv__title">{{ activeSession.title }}</h2>
            <span class="conv__badge" :class="{ 'is-human': activeSession.status === 'CLOSED' }">
              {{ activeSession.status === 'CLOSED' ? '已归档' : '进行中' }}
            </span>
            <span class="conv__conf num">{{ relativeTime(activeSession.updatedAt) }}</span>
          </div>

          <el-button
            v-if="activeSession.status !== 'CLOSED'"
            type="default"
            @click="archive"
          >
            归档会话
          </el-button>
        </header>

        <div ref="scroller" class="conv__scroll">
          <ChatBubble v-for="m in messages" :key="m.id" :message="m" show-trace />

          <EmptyState
            v-if="!messages.length"
            title="这个会话还没有消息"
            description="消费者发第一条消息后会出现在这里"
          />
        </div>

        <footer class="conv__composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            placeholder="发送消息，Agent 会流式回复（Enter 发送）"
            @keydown.enter.exact.prevent="send"
          />
          <div class="conv__composer-bar">
            <span class="conv__hint">发送后会触发 Agent 处理，回复以流式返回；人工接管能力待后端扩展</span>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="sending"
              :disabled="!input.trim() || activeSession.status === 'CLOSED'"
              @click="send"
            >
              发送
            </el-button>
          </div>
        </footer>
      </template>

      <EmptyState
        v-else
        size="lg"
        title="选择左侧会话"
        description="可以查看 Agent 与消费者的完整对话、工具调用轨迹与知识来源"
      />
    </section>
  </div>
</template>

<style scoped lang="scss">
.conv {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: var(--space-4);
  height: calc(100vh - var(--topbar-h) - var(--space-6) * 2);
}

/* 左列 */
.conv__list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.conv__search {
  padding: var(--space-4) var(--space-4) var(--space-3);
}

.conv__filters {
  display: flex;
  gap: 4px;
  padding: 0 var(--space-4) var(--space-3);
}

.conv__filter {
  padding: 4px 9px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--ink-50);
  font-size: 11.5px;
  color: var(--text-muted);
  cursor: pointer;

  &.is-active {
    background: var(--ink-900);
    color: #fff;
  }
}

.conv__sessions {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--space-3) var(--space-3);
  border-top: 1px solid var(--border-subtle);
  padding-top: var(--space-3);
}

.sitem {
  display: block;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 2px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--ink-50);
  }

  &.is-active {
    background: var(--brand-50);
    border-color: var(--brand-100);
  }
}

.sitem__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.sitem__name {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-strong);
}

.sitem__time {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.sitem__title {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.sitem__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
}

.sitem__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand-400);

  &.is-human {
    background: var(--amber-500);
  }
  &.is-closed {
    background: var(--ink-300);
  }
}

.sitem__status {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.sitem__low {
  padding: 0 5px;
  border-radius: var(--radius-xs);
  background: var(--amber-50);
  color: var(--amber-600);
  font-size: 10px;
}

.sitem__unread {
  margin-left: auto;
  padding: 0 5px;
  border-radius: var(--radius-pill);
  background: var(--red-500);
  color: #fff;
  font-size: 10px;
}

.conv__empty {
  padding: var(--space-8) 0;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

/* 右列 */
.conv__detail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.conv__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.conv__head-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.conv__title {
  font-size: var(--fs-md);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv__badge {
  flex: none;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 500;

  &.is-human {
    background: var(--amber-50);
    color: var(--amber-600);
  }
}

.conv__conf {
  flex: none;
  font-size: 11px;
  color: var(--text-subtle);
}

.conv__scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  background: var(--bg-subtle);
}

.conv__composer {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--border-subtle);

  :deep(.el-textarea__inner) {
    font-size: var(--fs-sm);
    line-height: 1.7;
  }
}

.conv__composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: 8px;
}

.conv__hint {
  font-size: 11px;
  color: var(--text-subtle);
}

@media (max-width: 980px) {
  .conv {
    grid-template-columns: 1fr;
    height: auto;
  }
  .conv__list {
    max-height: 320px;
  }
  .conv__detail {
    height: calc(100vh - var(--topbar-h) - 360px);
  }
}
</style>
