<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChatDotRound, Service } from '@element-plus/icons-vue'
import type { ChatMessage } from '@/api/types'
import { formatTime } from '@/utils/format'

const props = withDefaults(defineProps<{ message: ChatMessage; showTrace?: boolean }>(), {
  showTrace: false
})

const isUser = computed(() => props.message.role === 'user')
const isHuman = computed(() => props.message.role === 'human')
const showCitations = ref(false)
const showTools = ref(false)

const confidence = computed(() => props.message.confidence ?? 1)
const lowConfidence = computed(() => confidence.value < 0.6)
</script>

<template>
  <div class="msg" :class="{ 'is-user': isUser, 'is-human': isHuman }">
    <div v-if="!isUser" class="msg__avatar" :class="{ 'is-human': isHuman }">
      <el-icon :size="15">
        <Service v-if="isHuman" />
        <ChatDotRound v-else />
      </el-icon>
    </div>

    <div class="msg__body">
      <div v-if="!isUser" class="msg__meta">
        <span class="msg__author">{{ isHuman ? '人工客服' : '商户宝 Agent' }}</span>
        <span class="msg__time num">{{ formatTime(message.createdAt) }}</span>
        <span v-if="lowConfidence && !isHuman" class="msg__confidence">
          置信度 {{ Math.round(confidence * 100) }}%
        </span>
      </div>

      <div class="msg__bubble">
        <p v-for="(line, i) in message.content.split('\n')" :key="i" class="msg__line">
          {{ line }}
        </p>
      </div>

      <div v-if="message.citations?.length" class="msg__cites">
        <button type="button" class="msg__cites-toggle" @click="showCitations = !showCitations">
          引用 {{ message.citations.length }} 条知识
          <span class="msg__caret">{{ showCitations ? '−' : '+' }}</span>
        </button>

        <ul v-if="showCitations" class="msg__cite-list">
          <li v-for="(c, i) in message.citations" :key="i" class="msg__cite">
            <div class="msg__cite-head">
              <span class="msg__cite-name">{{ c.documentName }}</span>
              <span v-if="c.score" class="msg__cite-score num">相关度 {{ c.score.toFixed(2) }}</span>
            </div>
            <p class="msg__cite-snippet">{{ c.snippet }}</p>
          </li>
        </ul>
      </div>

      <div v-if="showTrace && message.toolCalls?.length" class="msg__trace">
        <button type="button" class="msg__trace-toggle" @click="showTools = !showTools">
          调用 {{ message.toolCalls.length }} 个只读工具
          <span class="msg__caret">{{ showTools ? '−' : '+' }}</span>
        </button>

        <div v-if="showTools" class="msg__tools">
          <span
            v-for="(t, i) in message.toolCalls"
            :key="i"
            class="msg__tool"
            :class="{ 'is-fail': !t.success }"
          >
            <i />
            {{ t.tool }}
            <em v-if="t.latencyMs" class="num">{{ t.latencyMs }}ms</em>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.msg {
  display: flex;
  gap: 10px;
  padding: 2px 0;

  &.is-user {
    flex-direction: row-reverse;

    .msg__body {
      align-items: flex-end;
    }

    .msg__bubble {
      background: var(--brand-500);
      color: #fff;
      border-color: transparent;
      border-radius: 14px 14px 4px 14px;
    }
  }
}

.msg__avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 9px;
  background: var(--brand-50);
  color: var(--brand-600);
  margin-top: 20px;

  &.is-human {
    background: var(--teal-50);
    color: var(--teal-600);
  }
}

.msg__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: min(76%, 620px);
}

.msg__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 2px;
}

.msg__author {
  font-size: var(--fs-xs);
  font-weight: 500;
  color: var(--text-body);
}

.msg__time {
  font-size: 11px;
  color: var(--text-subtle);
}

.msg__confidence {
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--amber-50);
  color: var(--amber-600);
  font-size: 10.5px;
  font-weight: 500;
}

.msg__bubble {
  padding: 12px 15px;
  border: 1px solid var(--border-subtle);
  border-radius: 14px 14px 14px 4px;
  background: var(--bg-surface);
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--text-body);
  box-shadow: var(--shadow-xs);
}

.msg__line + .msg__line {
  margin-top: 6px;
}

.msg__cites {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.msg__cites-toggle,
.msg__trace-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 3px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-pill);
  background: var(--bg-subtle);
  font-size: 11.5px;
  color: var(--text-muted);
  cursor: pointer;

  &:hover {
    border-color: var(--brand-200);
    color: var(--brand-600);
  }
}

.msg__caret {
  font-size: 12px;
  line-height: 1;
}

.msg__cite-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-left: 2px solid var(--brand-200);
  background: var(--brand-50);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.msg__cite-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.msg__cite-name {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--brand-700);
}

.msg__cite-score {
  font-size: 10.5px;
  color: var(--brand-500);
}

.msg__cite-snippet {
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--text-muted);
}

.msg__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.msg__tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: var(--radius-xs);
  background: var(--ink-50);
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-muted);

  i {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--green-500);
  }

  em {
    font-style: normal;
    color: var(--text-subtle);
  }

  &.is-fail {
    background: var(--red-50);
    color: var(--red-600);

    i {
      background: var(--red-500);
    }
  }
}

@media (max-width: 720px) {
  .msg__body {
    max-width: 86%;
  }
}
</style>
