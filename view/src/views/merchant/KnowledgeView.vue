<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadRawFile } from 'element-plus'
import { Document, Plus, RefreshRight } from '@element-plus/icons-vue'
import { listKnowledgeDocuments, uploadKnowledgeDocument } from '@/api/knowledge'
import { ApiError } from '@/api/request'
import type { KnowledgeDocStatus, KnowledgeDocumentView } from '@/api/types'
import { relativeTime } from '@/utils/format'

/**
 * 知识库文档（knowledge-v1）：员工同步导入 TXT/Markdown，
 * READY 后参与 Agent 的 RAG 检索；FAILED 不参与聊天。
 */

const docs = ref<KnowledgeDocumentView[]>([])
const loading = ref(true)
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loadingMore = ref(false)

const dialogVisible = ref(false)
const title = ref('')
const file = ref<File | null>(null)
const uploading = ref(false)

const MAX_SIZE = 1024 * 1024 // 契约：1 MiB

const statusMeta: Record<KnowledgeDocStatus, { label: string; cls: string }> = {
  PROCESSING: { label: '处理中', cls: 'is-processing' },
  READY: { label: '已就绪', cls: 'is-ready' },
  FAILED: { label: '失败', cls: 'is-failed' }
}

async function load(reset = false) {
  if (reset) {
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const page = await listKnowledgeDocuments(20, reset ? undefined : nextCursor.value || undefined)
    docs.value = reset ? page.items : [...docs.value, ...page.items]
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function onFileChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw as UploadRawFile | undefined
  if (!raw) return
  if (!/\.(txt|md)$/i.test(raw.name)) {
    ElMessage.warning('仅支持 UTF-8 的 .txt / .md 文件')
    return
  }
  if (raw.size > MAX_SIZE) {
    ElMessage.warning('文件超过 1 MiB 上限')
    return
  }
  file.value = raw
}

async function submit() {
  const trimmed = title.value.trim()
  if (!trimmed || trimmed.length > 200) {
    ElMessage.warning('标题为必填项，1–200 字符')
    return
  }
  if (!file.value) {
    ElMessage.warning('请选择要导入的 TXT/MD 文件')
    return
  }
  uploading.value = true
  try {
    const doc = await uploadKnowledgeDocument({ title: trimmed, file: file.value })
    docs.value.unshift(doc)
    ElMessage.success(
      doc.status === 'READY'
        ? `导入成功：已切分 ${doc.chunkCount} 片，Agent 现在可以引用它回答`
        : '导入已提交，文档处理完成后自动参与检索'
    )
    dialogVisible.value = false
    title.value = ''
    file.value = null
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 13501) ElMessage.error('RAG 功能未开启，请联系管理员开启后再导入')
      else if (e.code === 13502) ElMessage.error('向量化服务（Embedding/Milvus）暂不可用，请稍后重试')
      else if (e.status === 413) ElMessage.error('文件、码点或切片数量超限')
      else if (e.status === 415) ElMessage.error('仅支持 TXT/MD 文件')
      else ElMessage.error(e.message)
    }
  } finally {
    uploading.value = false
  }
}

onMounted(() => load(true))
</script>

<template>
  <div class="kb">
    <header class="kb__head">
      <div>
        <h2 class="kb__title">知识库文档</h2>
        <p class="kb__desc">
          导入 UTF-8 的 TXT / Markdown（≤1 MiB），同步切分并向量化；
          <b>已就绪</b>的文档才会被 Agent 检索引用，回答下方会展示命中的来源片段
        </p>
      </div>
      <div class="kb__actions">
        <el-button :icon="RefreshRight" @click="load(true)">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="dialogVisible = true">导入文档</el-button>
      </div>
    </header>

    <div v-if="loading" class="skeleton" style="height: 220px" />

    <section v-else class="panel">
      <div v-if="!docs.length" class="empty">
        <el-icon :size="34" color="#98A2B3"><Document /></el-icon>
        <p>还没有导入任何文档</p>
        <span>导入售后政策、产品说明书等资料后，Agent 就能引用它们回答客户问题</span>
      </div>

      <ul v-else class="list">
        <li v-for="d in docs" :key="d.documentId" class="row">
          <div class="row__main">
            <span class="row__title">{{ d.title }}</span>
            <span class="row__meta num">{{ d.createdAt.slice(0, 10) }} · {{ relativeTime(d.createdAt) }}</span>
          </div>
          <span class="row__chunks num">{{ d.chunkCount }} 片</span>
          <span class="row__status" :class="statusMeta[d.status].cls">
            {{ statusMeta[d.status].label }}
          </span>
        </li>
      </ul>

      <button
        v-if="hasMore"
        type="button"
        class="panel__more"
        :disabled="loadingMore"
        @click="load(false)"
      >
        {{ loadingMore ? '加载中…' : '加载更多' }}
      </button>
    </section>

    <el-dialog v-model="dialogVisible" title="导入知识文档" width="460px">
      <el-form label-width="72px" label-position="left">
        <el-form-item label="标题" required>
          <el-input
            v-model="title"
            maxlength="200"
            show-word-limit
            placeholder="例如：售后政策 2026 版"
          />
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload
            drag
            :limit="1"
            :auto-upload="false"
            accept=".txt,.md"
            :on-change="onFileChange"
            :on-exceed="() => ElMessage.warning('一次只能导入一个文件')"
          >
            <el-icon :size="30" color="#98A2B3"><Document /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">UTF-8 编码的 .txt / .md，最大 1 MiB，最多切分 200 片</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submit">同步导入</el-button>
      </template>
    </el-dialog>
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
  max-width: 680px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-subtle);

  b {
    color: var(--green-500);
    font-weight: 500;
  }
}

.kb__actions {
  display: flex;
  gap: var(--space-2);
}

.panel {
  padding: var(--space-3) var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.empty {
  padding: var(--space-7) 0;
  text-align: center;
  color: var(--text-subtle);

  p {
    margin-top: var(--space-3);
    font-size: var(--fs-sm);
    color: var(--text-muted);
  }

  span {
    font-size: var(--fs-xs);
  }
}

.row {
  display: grid;
  grid-template-columns: 1fr 80px 84px;
  align-items: center;
  gap: var(--space-4);
  padding: 12px 0;

  & + & {
    border-top: 1px solid var(--border-subtle);
  }
}

.row__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.row__title {
  font-size: var(--fs-sm);
  color: var(--text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__meta {
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--text-subtle);
}

.row__chunks {
  font-size: 11px;
  color: var(--text-muted);
  text-align: right;
}

.row__status {
  justify-self: end;
  padding: 2px 9px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 500;

  &.is-ready {
    background: var(--teal-50, #e6f7f3);
    color: var(--teal-600, #00a88f);
  }

  &.is-processing {
    background: var(--brand-50);
    color: var(--brand-600);
  }

  &.is-failed {
    background: var(--red-50, #fdecec);
    color: var(--red-500, #e5484d);
  }
}

.panel__more {
  width: 100%;
  padding: 10px;
  margin-top: var(--space-2);
  border: 1px dashed var(--border-subtle);
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--text-muted);
  cursor: pointer;

  &:hover:not(:disabled) {
    color: var(--brand-600);
    border-color: var(--brand-200);
  }
}

.el-upload__text {
  em {
    color: var(--brand-600);
    font-style: normal;
  }
}
</style>
