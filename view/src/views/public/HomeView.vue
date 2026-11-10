<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowRight, ChatDotRound } from '@element-plus/icons-vue'
import ProductCard from '@/components/ProductCard.vue'
import { listPublicProducts } from '@/api/catalog'
import type { ProductSummary } from '@/api/types'

const router = useRouter()
const keyword = ref('')
const hotProducts = ref<ProductSummary[]>([])
const loading = ref(true)

const categories = [
  { name: '充电器', count: 24, hint: '氮化镓 / 快充' },
  { name: '数据线', count: 18, hint: '100W / 编织' },
  { name: '移动电源', count: 9, hint: '磁吸 / 大容量' },
  { name: '耳机', count: 7, hint: 'TWS / 降噪' },
  { name: '车载充电', count: 6, hint: '车规级' },
  { name: '无线充', count: 4, hint: '磁吸支架' }
]

const metrics = [
  { value: '68', unit: '个', label: '在售 SKU', note: '覆盖手机 / 平板 / 轻薄本' },
  { value: '3.2', unit: 's', label: '平均首次响应', note: 'Agent 直答，不排队' },
  { value: '78', unit: '%', label: '自动解决率', note: '低于阈值自动转人工' },
  { value: '7×24', unit: '', label: '在线服务', note: '夜间也不漏单' }
]

const capabilities = [
  {
    no: '01',
    title: '商品与兼容性',
    desc: '按机型、接口类型、快充协议判断能不能用，回答里带 SKU 差异和价格，不泛泛而谈。',
    tags: ['SPU / SKU', '兼容性矩阵', '参数对照']
  },
  {
    no: '02',
    title: '库存、订单与物流',
    desc: '公开端只给「有货 / 少量 / 缺货」状态；登录后按本人身份查订单归属与物流轨迹。',
    tags: ['只读 Tool', '租户隔离', '归属校验']
  },
  {
    no: '03',
    title: '知识库与来源引用',
    desc: '说明书、售后政策、FAQ 切分入库，回答附来源片段；检索不到就转人工，不编造。',
    tags: ['RAG', '引用可追溯', '低置信降级']
  }
]

onMounted(async () => {
  try {
    const res = await listPublicProducts({ pageSize: 4, sort: 'recommend' })
    hotProducts.value = res.items
  } finally {
    loading.value = false
  }
})

function search() {
  const q = keyword.value.trim()
  router.push({ path: '/products', query: q ? { keyword: q } : {} })
}

function goCategory(name: string) {
  router.push({ path: '/products', query: { category: name } })
}
</script>

<template>
  <div class="home">
    <!-- Hero -->
    <section class="hero">
      <div class="container hero__grid">
        <div class="hero__copy">
          <span class="hero__badge">
            <i class="hero__badge-dot" />
            广州 · 3C 配件样板商户
            <span class="hero__badge-code">M_3C_DEMO</span>
          </span>

          <h1 class="hero__title">
            把售后问题交给 Agent，<br />
            把复杂留给系统。
          </h1>

          <p class="hero__lead">
            商品参数、SKU 差异、公开库存、订单物流、售后政策——
            消费者一句话问清楚，商户端随时接管。
          </p>

          <div class="hero__search">
            <el-input
              v-model="keyword"
              size="large"
              placeholder="试试：65W 充电器能给 MacBook 用吗？"
              :prefix-icon="Search"
              clearable
              @keyup.enter="search"
            />
            <el-button type="primary" size="large" @click="search">搜索商品</el-button>
          </div>

          <div class="hero__chips">
            <span class="hero__chips-label">常问：</span>
            <button
              v-for="chip in ['充电器兼容性', '港版插头区别', '订单到哪了', '保修怎么算']"
              :key="chip"
              class="hero__chip"
              @click="router.push({ path: '/qa', query: { q: chip } })"
            >
              {{ chip }}
            </button>
          </div>
        </div>

        <div class="hero__panel">
          <div class="preview">
            <header class="preview__head">
              <div class="preview__agent">
                <span class="preview__avatar">
                  <el-icon :size="15"><ChatDotRound /></el-icon>
                </span>
                <div>
                  <div class="preview__name">商户宝 Agent</div>
                  <div class="preview__status">在线 · 命中知识库 2 条</div>
                </div>
              </div>
              <span class="preview__tag">RAG</span>
            </header>

            <div class="preview__body">
              <div class="preview__msg preview__msg--user">
                65W 那个充电器，能给 MacBook Air 充吗？
              </div>

              <div class="preview__msg preview__msg--agent">
                <p>
                  可以。这款支持 PD3.0，单口最高 65W，MacBook Air 原装是 30W–35W，用它充完全够用。
                </p>
                <p class="preview__note">
                  注意：双口同时用时功率会重新分配（45W + 20W），给笔记本充电建议单口插。
                </p>

                <div class="preview__cites">
                  <span class="preview__cites-label">来源</span>
                  <span class="preview__cite">产品说明书.pdf</span>
                  <span class="preview__cite">兼容性说明.md</span>
                </div>

                <div class="preview__tools">
                  <span class="preview__tool">
                    <i />product.query · 42ms
                  </span>
                  <span class="preview__tool">
                    <i />inventory.availability · 28ms
                  </span>
                </div>
              </div>
            </div>

            <footer class="preview__foot">
              <span class="preview__input">继续追问，比如「港版插头有什么区别」</span>
              <span class="preview__send">→</span>
            </footer>
          </div>
        </div>
      </div>
    </section>

    <!-- 指标 -->
    <section class="container metrics">
      <div v-for="m in metrics" :key="m.label" class="metrics__item">
        <div class="metrics__value num">
          {{ m.value }}<span class="metrics__unit">{{ m.unit }}</span>
        </div>
        <div class="metrics__label">{{ m.label }}</div>
        <div class="metrics__note">{{ m.note }}</div>
      </div>
    </section>

    <!-- 分类 -->
    <section class="container section">
      <div class="section-head">
        <div>
          <div class="section-head__title">按品类逛</div>
          <div class="section-head__desc">全部为在售商品，库存状态实时同步</div>
        </div>
        <RouterLink to="/products" class="link-more">
          全部商品
          <el-icon :size="12"><ArrowRight /></el-icon>
        </RouterLink>
      </div>

      <div class="cats">
        <button
          v-for="c in categories"
          :key="c.name"
          class="cats__item"
          type="button"
          @click="goCategory(c.name)"
        >
          <span class="cats__name">{{ c.name }}</span>
          <span class="cats__hint">{{ c.hint }}</span>
          <span class="cats__count num">{{ c.count }} 款</span>
        </button>
      </div>
    </section>

    <!-- 热销 -->
    <section class="container section">
      <div class="section-head">
        <div>
          <div class="section-head__title">热销商品</div>
          <div class="section-head__desc">点开任意商品，右下角可以直接问 Agent</div>
        </div>
        <RouterLink to="/products" class="link-more">
          查看全部
          <el-icon :size="12"><ArrowRight /></el-icon>
        </RouterLink>
      </div>

      <div v-if="loading" class="grid-4">
        <div v-for="i in 4" :key="i" class="skeleton" style="height: 320px" />
      </div>
      <div v-else class="grid-4">
        <ProductCard v-for="p in hotProducts" :key="p.id" :product="p" />
      </div>
    </section>

    <!-- 能力 -->
    <section class="caps">
      <div class="container">
        <div class="caps__head">
          <h2>Agent 能替你答什么</h2>
          <p>只读工具 + 知识库检索，回答有据可查；答不好就交给人。</p>
        </div>
        <div class="caps__grid">
          <article v-for="c in capabilities" :key="c.no" class="caps__card">
            <span class="caps__no num">{{ c.no }}</span>
            <h3>{{ c.title }}</h3>
            <p>{{ c.desc }}</p>
            <div class="caps__tags">
              <span v-for="t in c.tags" :key="t">{{ t }}</span>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="container">
      <div class="cta">
        <div class="cta__copy">
          <h2>有具体问题？直接问，不用翻说明书。</h2>
          <p>支持手机号登录，对话和订单都在一个地方。</p>
        </div>
        <div class="cta__actions">
          <el-button type="primary" size="large" @click="router.push('/consumer/chat')">
            开始对话
          </el-button>
          <el-button size="large" @click="router.push('/qa')">先看看公开问答</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home {
  padding-bottom: var(--space-4);
}

/* ---------------- Hero ---------------- */
.hero {
  position: relative;
  padding: var(--space-16) 0 var(--space-12);
  background:
    radial-gradient(900px 420px at 78% -10%, #e8efff 0%, rgba(255, 255, 255, 0) 62%),
    linear-gradient(180deg, #fafcff 0%, var(--bg-page) 100%);
  border-bottom: 1px solid var(--border-subtle);
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: radial-gradient(var(--ink-200) 1px, transparent 1px);
    background-size: 22px 22px;
    opacity: 0.35;
    mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.5), transparent 75%);
    pointer-events: none;
  }
}

.hero__grid {
  position: relative;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  gap: var(--space-16);
  align-items: center;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px 5px 10px;
  border: 1px solid var(--brand-100);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.7);
  font-size: var(--fs-xs);
  color: var(--brand-600);
  font-weight: 500;
}

.hero__badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--teal-500);
  box-shadow: 0 0 0 3px rgba(0, 168, 143, 0.14);
}

.hero__badge-code {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-subtle);
  padding-left: 8px;
  border-left: 1px solid var(--border-subtle);
}

.hero__title {
  margin-top: var(--space-5);
  font-size: var(--fs-4xl);
  line-height: 1.22;
  letter-spacing: -0.025em;
  color: var(--ink-900);
}

.hero__lead {
  margin-top: var(--space-4);
  max-width: 460px;
  font-size: var(--fs-md);
  line-height: var(--lh-loose);
  color: var(--text-muted);
}

.hero__search {
  display: flex;
  gap: 10px;
  margin-top: var(--space-6);
  max-width: 520px;

  :deep(.el-input) {
    flex: 1;
  }

  :deep(.el-input__wrapper) {
    background: var(--bg-surface);
    box-shadow:
      0 0 0 1px var(--border-default) inset,
      var(--shadow-sm);
  }
}

.hero__chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: var(--space-4);
}

.hero__chips-label {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.hero__chip {
  padding: 4px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-pill);
  background: var(--bg-surface);
  font-size: var(--fs-xs);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);

  &:hover {
    border-color: var(--brand-200);
    color: var(--brand-600);
    background: var(--brand-50);
  }
}

/* ---------------- 对话预览 ---------------- */
.preview {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  transform: rotate(0.4deg);
}

.preview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.preview__agent {
  display: flex;
  align-items: center;
  gap: 10px;
}

.preview__avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--brand-50);
  color: var(--brand-600);
}

.preview__name {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-strong);
}

.preview__status {
  font-size: 11px;
  color: var(--text-subtle);
}

.preview__tag {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--teal-50);
  color: var(--teal-600);
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.preview__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
}

.preview__msg {
  font-size: var(--fs-sm);
  line-height: var(--lh-loose);
}

.preview__msg--user {
  align-self: flex-end;
  max-width: 82%;
  padding: 10px 14px;
  border-radius: 14px 14px 4px 14px;
  background: var(--brand-500);
  color: #fff;
}

.preview__msg--agent {
  padding: 14px 16px;
  border-radius: 14px 14px 14px 4px;
  background: var(--ink-50);
  color: var(--text-body);
}

.preview__note {
  margin-top: 8px;
  color: var(--text-muted);
}

.preview__cites {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--border-default);
}

.preview__cites-label {
  font-size: 10.5px;
  color: var(--text-subtle);
}

.preview__cite {
  padding: 2px 7px;
  border-radius: var(--radius-xs);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  font-size: 10.5px;
  color: var(--text-muted);
}

.preview__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.preview__tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-subtle);

  i {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--green-500);
  }
}

.preview__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px var(--space-5) var(--space-5);
}

.preview__input {
  flex: 1;
  padding: 9px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-pill);
  background: var(--bg-subtle);
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.preview__send {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--brand-500);
  color: #fff;
  font-size: 15px;
}

/* ---------------- 指标 ---------------- */
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-top: calc(var(--space-8) * -1);
  position: relative;
  z-index: 2;
}

.metrics__item {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.metrics__value {
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.metrics__unit {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-muted);
  margin-left: 2px;
}

.metrics__label {
  margin-top: 6px;
  font-size: var(--fs-sm);
  color: var(--text-body);
}

.metrics__note {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

/* ---------------- 通用区块 ---------------- */
.section {
  margin-top: var(--space-16);
}

.link-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-sm);
  color: var(--text-muted);
  transition: color var(--dur-fast) var(--ease);

  &:hover {
    color: var(--brand-600);
  }
}

.grid-4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-5);
}

/* ---------------- 分类 ---------------- */
.cats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--space-3);
}

.cats__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-4) var(--space-4) var(--space-3);
  text-align: left;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);

  &:hover {
    border-color: var(--brand-200);
    background: var(--brand-50);
    transform: translateY(-2px);
  }
}

.cats__name {
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--text-strong);
}

.cats__hint {
  font-size: 11px;
  color: var(--text-subtle);
}

.cats__count {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--brand-600);
}

/* ---------------- 能力 ---------------- */
.caps {
  margin-top: var(--space-20);
  padding: var(--space-16) 0;
  background: var(--ink-900);
  color: #fff;
}

.caps__head {
  max-width: 560px;

  h2 {
    font-size: var(--fs-2xl);
    color: #fff;
    letter-spacing: -0.02em;
  }

  p {
    margin-top: 10px;
    font-size: var(--fs-sm);
    color: rgba(255, 255, 255, 0.56);
    line-height: var(--lh-loose);
  }
}

.caps__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-top: var(--space-8);
}

.caps__card {
  padding: var(--space-6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.03);
  transition: background var(--dur) var(--ease);

  &:hover {
    background: rgba(255, 255, 255, 0.06);
  }

  h3 {
    margin-top: var(--space-4);
    font-size: var(--fs-lg);
    color: #fff;
    font-weight: 500;
  }

  p {
    margin-top: 10px;
    font-size: var(--fs-sm);
    line-height: var(--lh-loose);
    color: rgba(255, 255, 255, 0.58);
  }
}

.caps__no {
  font-size: var(--fs-xs);
  color: rgba(255, 255, 255, 0.32);
  letter-spacing: 0.08em;
}

.caps__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--space-4);

  span {
    padding: 2px 8px;
    border-radius: var(--radius-pill);
    background: rgba(255, 255, 255, 0.08);
    font-size: 11px;
    color: rgba(255, 255, 255, 0.72);
  }
}

/* ---------------- CTA ---------------- */
.cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-8);
  margin-top: var(--space-16);
  padding: var(--space-10) var(--space-10);
  border: 1px solid var(--brand-100);
  border-radius: var(--radius-xl);
  background: linear-gradient(120deg, #eef2ff 0%, #f7faff 55%, #eefaf7 100%);
}

.cta__copy {
  h2 {
    font-size: var(--fs-xl);
    letter-spacing: -0.02em;
  }

  p {
    margin-top: 8px;
    font-size: var(--fs-sm);
    color: var(--text-muted);
  }
}

.cta__actions {
  display: flex;
  gap: 10px;
  flex: none;
}

@media (max-width: 1080px) {
  .hero__grid {
    grid-template-columns: 1fr;
    gap: var(--space-10);
  }
  .hero__panel {
    max-width: 520px;
  }
  .cats {
    grid-template-columns: repeat(3, 1fr);
  }
  .grid-4,
  .metrics,
  .caps__grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .metrics {
    margin-top: var(--space-8);
  }
}

@media (max-width: 640px) {
  .hero {
    padding: var(--space-10) 0;
  }
  .hero__title {
    font-size: var(--fs-2xl);
  }
  .hero__search {
    flex-direction: column;
  }
  .grid-4,
  .metrics,
  .caps__grid,
  .cats {
    grid-template-columns: 1fr;
  }
  .cta {
    flex-direction: column;
    align-items: flex-start;
    padding: var(--space-6);
  }
}
</style>
