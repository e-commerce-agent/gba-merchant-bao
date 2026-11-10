<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Promotion } from '@element-plus/icons-vue'
import ChatBubble from '@/components/ChatBubble.vue'
import { createSession, listMessages, listSessions, sendMessage } from '@/api/chat'
import { getPublicProduct } from '@/api/catalog'
import type { ChatMessage, ChatSession, ProductDetail } from '@/api/types'

const route = useRoute()
const router = useRouter()

const sessions = ref<ChatSession[]>([])
const messages = ref<ChatMessage[]>([])
const sessionId = ref('')
const session = computed(() => sessions.value.find((s) => s.id === sessionId.value))
const input = ref('')
const sending = ref(false)
const loading = ref(true)
const scroller = ref<HTMLElement>()
const contextProduct = ref<ProductDetail | null>(null)

const quickPrompts = [
  '我的订单到哪了',
  '这款充电器支持 MacBook 吗',
  '港版插头和国标有什么区别',
  '保修期内坏了怎么换'
]

const statusText = computed(() => {
  const s = session.value?.status
  if (s === 'HUMAN') return '人工客服接入中'
  if (s === 'CLOSED') return '会话已结束'
  return 'Agent 在线'
})

async function scrollToBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

async function loadMessages(id: string) {
  messages.value = await listMessages(id)
  await scrollToBottom()
}

async function init() {
  loading.value = true
  try {
    sessions.value = await listSessions('consumer')
    const routeId = String(route.params.id || '')
    const target = sessions.value.find((s) => s.id === routeId) || sessions.value[0]
    if (target) {
      sessionId.value = target.id
      await loadMessages(target.id)
    }

    const productId = String(route.query.productId || '')
    if (productId) {
      try {
        contextProduct.value = await getPublicProduct(productId)
      } catch {
        contextProduct.value = null
      }
    }

    if (route.query.q) {
      input.value = String(route.query.q)
    }
  } finally {
    loading.value = false
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return

  if (!sessionId.value) {
    const created = await createSession(text.slice(0, 20))
    sessions.value.unshift(created)
    sessionId.value = created.id
  }

  const localMsg: ChatMessage = {
    id: `local-${Date.now()}`,
    sessionId: sessionId.value,
    role: 'user',
    content: text,
    createdAt: new Date().toISOString()
  }

  messages.value.push(localMsg)
  input.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    const reply = await sendMessage(sessionId.value, text)
    messages.value.push(reply)
    await scrollToBottom()
    // 同步会话列表里的最后一条消息
    const idx = sessions.value.findIndex((s) => s.id === sessionId.value)
    if (idx >= 0) {
      sessions.value[idx] = {
        ...sessions.value[idx],
        lastMessage: reply.content.slice(0, 40),
        updatedAt: reply.createdAt,
        status: reply.confidence !== undefined && reply.confidence < 0.6 ? 'HUMAN' : sessions.value[idx].status
      }
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function newSession() {
  const created = await createSession('新的咨询')
  sessions.value.unshift(created)
  sessionId.value = created.id
  messages.value = []
  router.replace('/consumer/chat')
}

function useQuick(text: string) {
  input.value = text
  send()
}

watch(
  () => route.params.id,
  (id) => {
    if (id && id !== sessionId.value) {
      sessionId.value = String(id)
      loadMessages(String(id))
    }
  }
)

onMounted(init)
</script>

<template>
  <div class="chat">
    <header class="chat__head">
      <div class="chat__head-left">
        <h2 class="chat__title">{{ session?.title || '新的咨询' }}</h2>
        <span class="chat__status" :class="{ 'is-human': session?.status === 'HUMAN' }">
          <i />
          {{ statusText }}
        </span>
        <span v-if="session" class="chat__count num">{{ messages.length }} 条消息</span>
      </div>

      <el-button :icon="Plus" @click="newSession">新建会话</el-button>
    </header>

    <div v-if="contextProduct" class="chat__context">
      <span class="chat__context-label">正在咨询</span>
      <span class="chat__context-name">{{ contextProduct.name }}</span>
      <button class="chat__context-close" type="button" @click="contextProduct = null">×</button>
    </div>

    <div ref="scroller" class="chat__scroll">
      <div v-if="loading" class="chat__loading">
        <div class="skeleton" style="height: 64px; width: 60%" />
        <div class="skeleton" style="height: 96px; width: 72%; margin-left: auto" />
      </div>

      <template v-else>
        <div v-if="!messages.length" class="chat__intro">
          <div class="chat__intro-card">
            <h3>你好，我是商户宝 Agent</h3>
            <p>
              商品参数、兼容性、库存状态、订单物流、售后政策，都可以直接问我。
              回答会附上知识库来源；不确定的问题我会转给人工客服。
            </p>
            <div class="chat__quick">
              <button v-for="q in quickPrompts" :key="q" type="button" @click="useQuick(q)">
                {{ q }}
              </button>
            </div>
          </div>
        </div>

        <ChatBubble v-for="m in messages" :key="m.id" :message="m" show-trace />

        <div v-if="sending" class="chat__typing">
          <span /><span /><span />
        </div>
      </template>
    </div>

    <footer class="chat__composer">
      <div class="chat__composer-box">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          placeholder="描述你的问题，例如：65W 充电器能给我的 MacBook Air 用吗？"
          @keydown.enter.exact.prevent="send"
        />
        <div class="chat__composer-bar">
          <span class="chat__hint">Enter 发送 · Shift + Enter 换行 · 回答附知识来源</span>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="sending"
            :disabled="!input.trim()"
            @click="send"
          >
            发送
          </el-button>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--topbar-h) - var(--space-6) * 2);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.chat__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.chat__head-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.chat__title {
  font-size: var(--fs-md);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--green-50);
  color: var(--green-500);
  font-size: 11px;
  font-weight: 500;
  flex: none;

  i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
  }

  &.is-human {
    background: var(--amber-50);
    color: var(--amber-600);
  }
}

.chat__count {
  font-size: 11px;
  color: var(--text-subtle);
  flex: none;
}

.chat__context {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px var(--space-5);
  background: var(--brand-50);
  border-bottom: 1px solid var(--brand-100);
  font-size: var(--fs-xs);
}

.chat__context-label {
  color: var(--brand-500);
  font-weight: 500;
}

.chat__context-name {
  color: var(--brand-700);
}

.chat__context-close {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--brand-500);
  font-size: 15px;
  cursor: pointer;
  line-height: 1;
}

.chat__scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6) var(--space-6) var(--space-4);
  background:
    radial-gradient(600px 300px at 50% 0%, #fbfcff 0%, rgba(255, 255, 255, 0) 70%),
    var(--bg-surface);
}

.chat__loading {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.chat__intro {
  display: flex;
  justify-content: center;
  padding: var(--space-8) 0 var(--space-6);
}

.chat__intro-card {
  max-width: 520px;
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--bg-subtle);

  h3 {
    font-size: var(--fs-lg);
    font-weight: 500;
  }

  p {
    margin-top: 8px;
    font-size: var(--fs-sm);
    line-height: var(--lh-loose);
    color: var(--text-muted);
  }
}

.chat__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: var(--space-4);

  button {
    padding: 6px 12px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-pill);
    background: var(--bg-surface);
    font-size: var(--fs-xs);
    color: var(--text-body);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease);

    &:hover {
      border-color: var(--brand-300);
      color: var(--brand-600);
      background: var(--brand-50);
    }
  }
}

.chat__typing {
  display: inline-flex;
  gap: 4px;
  margin: var(--space-2) 0 var(--space-2) 40px;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--ink-50);

  span {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--ink-300);
    animation: blink 1.2s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

.chat__composer {
  padding: var(--space-4) var(--space-5) var(--space-5);
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}

.chat__composer-box {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 6px;
  transition: border-color var(--dur-fast) var(--ease);

  &:focus-within {
    border-color: var(--brand-300);
    box-shadow: 0 0 0 3px var(--brand-50);
  }

  :deep(.el-textarea__inner) {
    box-shadow: none;
    padding: 8px 10px;
    font-size: var(--fs-sm);
    line-height: 1.7;
  }
}

.chat__composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 0 4px 2px 10px;
}

.chat__hint {
  font-size: 11px;
  color: var(--text-subtle);
}

@media (max-width: 720px) {
  .chat {
    height: calc(100vh - var(--topbar-h) - var(--space-4) * 2);
  }
  .chat__hint {
    display: none;
  }
  .chat__scroll {
    padding: var(--space-4);
  }
}
</style>
