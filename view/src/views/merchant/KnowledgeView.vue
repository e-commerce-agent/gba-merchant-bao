<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Plus } from '@element-plus/icons-vue'
import { listDocumentTasks, listKnowledgeBases } from '@/api/knowledge'
import type { DocumentTask, KnowledgeBase } from '@/api/types'
import { formatFileSize, relativeTime } from '@/utils/format'

const router = useRouter()
const bases = ref<KnowledgeBase[]>([])
const tasks = ref<DocumentTask[]>([])
const loading = ref(true)

const statusText: Record<string, string> = {
  QUEUED: '排队中',
  PARSING: '解析中',
  CHUNKING: '切分中',
  EMBEDDING: '向量化中',
  SUCCEEDED: '已完成',
  FAILED: '失败'
}

onMounted(async () => {
  try {
    const [b, t] = await Promise.all([listKnowledgeBases(), listDocumentTasks()])
    bases.value = b
    tasks.value = t
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="kb">
    <header class="kb__head">
      <div>
        <h2 class="kb__title">知识库</h2>
        <p class="kb__desc">
          文档上传后进入异步任务：解析 → 切分 → 向量化 → 入库，Agent 回答时按相关度检索并回引来源
        </p>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        @click="ElMessage.info('写接口待后端交付：POST /v1/knowledge-bases')"
      >
        新建知识库
      </el-button>
    </header>

    <div v-if="loading" class="kb__grid">
      <div v-for="i in 2" :key="i" class="skeleton" style="height: 160px" />
    </div>

    <div v-else class="kb__grid">
      <article v-for="b in bases" :key="b.id" class="kbcard">
        <div class="kbcard__icon">
          <el-icon :size="18"><Collection /></el-icon>
        </div>
        <h3 class="kbcard__name">{{ b.name }}</h3>
        <p class="kbcard__desc">{{ b.description }}</p>

        <div class="kbcard__stats">
          <div class="kbcard__stat">
            <span class="num">{{ b.documentCount }}</span>
            <em>文档</em>
          </div>
          <div class="kbcard__stat">
            <span class="num">{{ b.chunkCount }}</span>
            <em>切片</em>
          </div>
          <div class="kbcard__stat">
            <span class="num">{{ relativeTime(b.updatedAt) }}</span>
            <em>更新</em>
          </div>
        </div>
      </article>
    </div>

    <section class="recent">
      <header class="recent__head">
        <h3>最近导入</h3>
        <button type="button" class="recent__more" @click="router.push('/merchant/tasks')">
          查看全部任务
        </button>
      </header>

      <ul class="recent__list">
        <li v-for="t in tasks.slice(0, 4)" :key="t.id" class="rrow">
          <div class="rrow__name">
            <span class="rrow__file">{{ t.fileName }}</span>
            <span class="rrow__size num">{{ formatFileSize(t.fileSize) }}</span>
          </div>
          <span class="rrow__status" :class="`is-${t.status.toLowerCase()}`">
            {{ statusText[t.status] }}
          </span>
          <div class="rrow__progress">
            <el-progress
              :percentage="t.progress"
              :show-text="false"
              :stroke-width="5"
              :color="t.status === 'FAILED' ? '#E5484D' : '#3056D3'"
            />
          </div>
          <span class="rrow__chunks num">{{ t.chunkCount }} 片</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped lang="scss">
.kb__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.kb__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.kb__desc {
  margin-top: 6px;
  max-width: 640px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-subtle);
}

.kb__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-4);
}

.kbcard {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.kbcard__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand-50);
  color: var(--brand-600);
}

.kbcard__name {
  margin-top: var(--space-3);
  font-size: var(--fs-md);
  font-weight: 500;
}

.kbcard__desc {
  margin-top: 6px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-muted);
  min-height: 36px;
}

.kbcard__stats {
  display: flex;
  gap: var(--space-6);
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-subtle);
}

.kbcard__stat {
  display: flex;
  flex-direction: column;

  span {
    font-size: var(--fs-base);
    font-weight: 500;
    color: var(--text-strong);
  }

  em {
    font-style: normal;
    font-size: 10.5px;
    color: var(--text-subtle);
  }
}

.recent {
  margin-top: var(--space-6);
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.recent__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);

  h3 {
    font-size: var(--fs-base);
    font-weight: 500;
  }
}

.recent__more {
  border: none;
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--brand-600);
  cursor: pointer;
}

.rrow {
  display: grid;
  grid-template-columns: 1fr 88px 140px 60px;
  align-items: center;
  gap: var(--space-4);
  padding: 10px 0;

  & + & {
    border-top: 1px solid var(--border-subtle);
  }
}

.rrow__name {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.rrow__file {
  font-size: var(--fs-sm);
  color: var(--text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rrow__size {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.rrow__status {
  font-size: 11.5px;

  &.is-succeeded {
    color: var(--green-500);
  }
  &.is-failed {
    color: var(--red-500);
  }
  &.is-queued {
    color: var(--text-subtle);
  }
  &.is-parsing,
  &.is-chunking,
  &.is-embedding {
    color: var(--brand-600);
  }
}

.rrow__chunks {
  font-size: 11px;
  color: var(--text-subtle);
  text-align: right;
}

@media (max-width: 860px) {
  .rrow {
    grid-template-columns: 1fr 80px;
    row-gap: 6px;
  }
  .rrow__progress,
  .rrow__chunks {
    display: none;
  }
}
</style>
