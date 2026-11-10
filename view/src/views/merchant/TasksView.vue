<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listDocumentTasks, retryDocumentTask } from '@/api/knowledge'
import type { DocumentTask, DocumentTaskStatus } from '@/api/types'
import { formatDateTime, formatFileSize } from '@/utils/format'

const tasks = ref<DocumentTask[]>([])
const loading = ref(true)
const retrying = ref('')

const meta: Record<DocumentTaskStatus, { label: string; tone: string }> = {
  QUEUED: { label: '排队中', tone: 'muted' },
  PARSING: { label: '解析中', tone: 'run' },
  CHUNKING: { label: '切分中', tone: 'run' },
  EMBEDDING: { label: '向量化中', tone: 'run' },
  SUCCEEDED: { label: '已完成', tone: 'ok' },
  FAILED: { label: '失败', tone: 'fail' }
}

const stats = computed(() => ({
  total: tasks.value.length,
  running: tasks.value.filter((t) => ['QUEUED', 'PARSING', 'CHUNKING', 'EMBEDDING'].includes(t.status)).length,
  failed: tasks.value.filter((t) => t.status === 'FAILED').length,
  chunks: tasks.value.reduce((sum, t) => sum + t.chunkCount, 0)
}))

async function fetchTasks() {
  loading.value = true
  try {
    tasks.value = await listDocumentTasks()
  } finally {
    loading.value = false
  }
}

async function retry(task: DocumentTask) {
  retrying.value = task.id
  try {
    await retryDocumentTask(task.id)
    ElMessage.success('已重新入队')
    await fetchTasks()
  } catch (e) {
    ElMessage.error((e as Error).message || '重投失败')
  } finally {
    retrying.value = ''
  }
}

onMounted(fetchTasks)
</script>

<template>
  <div class="tasks">
    <header class="tasks__head">
      <div>
        <h2 class="tasks__title">文档任务</h2>
        <p class="tasks__desc">
          上传后的解析与向量化走 RabbitMQ 异步任务；失败可人工重投，进度由任务表回写
        </p>
      </div>
      <el-button :icon="Refresh" @click="fetchTasks">刷新</el-button>
    </header>

    <div class="stats">
      <div class="stat">
        <span class="stat__label">任务总数</span>
        <span class="stat__value num">{{ stats.total }}</span>
      </div>
      <div class="stat">
        <span class="stat__label">进行中</span>
        <span class="stat__value num">{{ stats.running }}</span>
      </div>
      <div class="stat">
        <span class="stat__label">失败</span>
        <span class="stat__value num" :class="{ 'is-danger': stats.failed > 0 }">{{ stats.failed }}</span>
      </div>
      <div class="stat">
        <span class="stat__label">累计切片</span>
        <span class="stat__value num">{{ stats.chunks }}</span>
      </div>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="tasks">
        <el-table-column label="文档" min-width="280">
          <template #default="{ row }">
            <div class="doc">
              <span class="doc__name">{{ row.fileName }}</span>
              <span class="doc__size num">{{ formatFileSize(row.fileSize) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="badge" :class="`is-${meta[row.status as DocumentTaskStatus].tone}`">
              {{ meta[row.status as DocumentTaskStatus].label }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :stroke-width="5"
              :show-text="false"
              :color="row.status === 'FAILED' ? '#E5484D' : '#3056D3'"
            />
            <span class="progress-text num">{{ row.progress }}%</span>
          </template>
        </el-table-column>

        <el-table-column label="切片" width="90" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.chunkCount || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">
            <span class="num time">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="130" align="right">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
              <el-button
                v-if="row.status === 'FAILED'"
                link
                type="primary"
                :loading="retrying === row.id"
                @click="retry(row)"
              >
                重新投递
              </el-button>
              <span v-else class="err">查看原因</span>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <p class="tasks__note">
      说明：进度查询对应 GET /v1/document-tasks/{taskId}，重投对应
      POST /v1/document-tasks/{taskId}:retry（命令式接口，冒号风格）。
    </p>
  </div>
</template>

<style scoped lang="scss">
.tasks__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.tasks__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.tasks__desc {
  margin-top: 6px;
  max-width: 640px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-subtle);
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-4) var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.stat__label {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.stat__value {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--ink-900);

  &.is-danger {
    color: var(--red-500);
  }
}

.table-card {
  padding: var(--space-2) var(--space-4) var(--space-3);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.doc {
  display: flex;
  flex-direction: column;
}

.doc__name {
  font-size: var(--fs-sm);
  color: var(--text-strong);
}

.doc__size {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.badge {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;

  &.is-ok {
    background: var(--green-50);
    color: var(--green-500);
  }
  &.is-run {
    background: var(--brand-50);
    color: var(--brand-600);
  }
  &.is-fail {
    background: var(--red-50);
    color: var(--red-500);
  }
  &.is-muted {
    background: var(--ink-100);
    color: var(--text-subtle);
  }
}

.progress-text {
  display: block;
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--text-subtle);
}

.time {
  font-size: 11.5px;
  color: var(--text-muted);
}

.err {
  font-size: var(--fs-xs);
  color: var(--red-500);
  cursor: help;
}

.muted {
  color: var(--text-subtle);
}

.tasks__note {
  margin-top: var(--space-3);
  font-size: 11px;
  color: var(--text-subtle);
  line-height: 1.7;
}

@media (max-width: 860px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
