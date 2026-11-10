<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { ChatDotRound } from '@element-plus/icons-vue'

const router = useRouter()
const keyword = ref('')
const activeCat = ref('全部')
const openId = ref('Q1')

const cats = ['全部', '兼容性', '库存', '物流', '售后']

const faqs = [
  {
    id: 'Q1',
    cat: '兼容性',
    question: '65W 氮化镓充电器能给 MacBook Air 充电吗？',
    answer:
      '可以。该型号支持 PD3.0 协议，USB-C 单口最高 65W，MacBook Air 原装充电器为 30W–35W，用它充电速度更快。双口同时使用时功率会重新分配为 45W + 20W，给笔记本充电建议只插一个口。',
    source: '极闪 GX-65W-GaN 产品说明书.pdf'
  },
  {
    id: 'Q2',
    cat: '兼容性',
    question: '国标插头和港版英标插头有什么区别？',
    answer:
      '功率与外观一致，区别只在插脚：国标为两脚扁插，英标（港版）为三脚方插，适用于中国香港、英国、新加坡等地区。港版价格比国标贵 ¥10。',
    source: '极闪 产品规格对照表.xlsx'
  },
  {
    id: 'Q3',
    cat: '库存',
    question: '为什么商品页不显示具体库存数量？',
    answer:
      '公开商城面向未登录访问者，只展示「有货 / 仅剩少量 / 暂时缺货」三种状态。具体库存数量属于商户经营数据，只在商户端按权限可见。需要确认是否能当天发货，可以登录后咨询客服。',
    source: '公开库存展示规范.md'
  },
  {
    id: 'Q4',
    cat: '物流',
    question: '广东省内一般多久能到？',
    answer:
      '默认顺丰速运，广东省内次日达，跨省 2–3 天。下单后当天 18:00 前付款一般当天出库，18:00 后顺延至次日。物流轨迹可在「我的订单」中查看。',
    source: '承运商时效说明.md'
  },
  {
    id: 'Q5',
    cat: '售后',
    question: '保修期多久？人为损坏算吗？',
    answer:
      '7 天内支持无理由退换（商品需保持完好、配件齐全），18 个月内非人为损坏只换不修。进水、摔裂、私自拆修属于人为损坏，不在保修范围内。质量问题产生的运费由商户承担。',
    source: '售后政策 2026 版.docx'
  },
  {
    id: 'Q6',
    cat: '售后',
    question: '充电宝可以无理由退货吗？',
    answer:
      '可以，但电池类商品需要保持外观无划痕、包装完整、未激活使用，且需在签收后 7 天内申请。已激活或外观有使用痕迹的按保修流程处理。',
    source: '售后政策 2026 版.docx'
  }
]

const filtered = computed(() =>
  faqs.filter((f) => {
    const catOk = activeCat.value === '全部' || f.cat === activeCat.value
    const kw = keyword.value.trim()
    const kwOk = !kw || f.question.includes(kw) || f.answer.includes(kw)
    return catOk && kwOk
  })
)

function toggle(id: string) {
  openId.value = openId.value === id ? '' : id
}

function askMore(question: string) {
  router.push({ path: '/consumer/chat', query: { q: question } })
}
</script>

<template>
  <div class="container qa">
    <header class="qa__head">
      <div>
        <span class="qa__badge">公开知识问答 · 无需登录</span>
        <h1 class="qa__title">先看这里，能自己解决就别等客服</h1>
        <p class="qa__desc">
          以下内容来自商户知识库，回答均附来源。更具体的问题（涉及你的订单）需要登录后咨询 Agent。
        </p>
      </div>

      <el-input
        v-model="keyword"
        class="qa__search"
        placeholder="搜索问题关键词"
        :prefix-icon="Search"
        clearable
      />
    </header>

    <div class="qa__body">
      <aside class="qa__side">
        <div class="qa__side-title">问题分类</div>
        <button
          v-for="c in cats"
          :key="c"
          type="button"
          class="qa__cat"
          :class="{ 'is-active': activeCat === c }"
          @click="activeCat = c"
        >
          {{ c }}
          <span class="qa__cat-count num">
            {{ c === '全部' ? faqs.length : faqs.filter((f) => f.cat === c).length }}
          </span>
        </button>

        <div class="qa__side-card">
          <div class="qa__side-card-title">没找到答案？</div>
          <p>登录后可以直接问 Agent，支持查你自己的订单和物流。</p>
          <el-button type="primary" class="qa__side-btn" @click="router.push('/consumer/chat')">
            问 Agent
          </el-button>
        </div>
      </aside>

      <main class="qa__main">
        <div v-for="f in filtered" :key="f.id" class="qa__item" :class="{ 'is-open': openId === f.id }">
          <button type="button" class="qa__q" @click="toggle(f.id)">
            <span class="qa__cat-tag">{{ f.cat }}</span>
            <span class="qa__q-text">{{ f.question }}</span>
            <span class="qa__q-toggle">{{ openId === f.id ? '−' : '+' }}</span>
          </button>

          <div v-if="openId === f.id" class="qa__a">
            <p class="qa__a-text">{{ f.answer }}</p>
            <div class="qa__a-foot">
              <span class="qa__source">来源：{{ f.source }}</span>
              <button class="qa__more" type="button" @click="askMore(f.question)">
                <el-icon :size="13"><ChatDotRound /></el-icon>
                继续追问
              </button>
            </div>
          </div>
        </div>

        <div v-if="!filtered.length" class="qa__empty">
          没有匹配的问题，试试换个关键词，或者登录后直接问 Agent。
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.qa {
  padding-top: var(--space-10);
  padding-bottom: var(--space-10);
}

.qa__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-8);
  padding-bottom: var(--space-8);
  border-bottom: 1px solid var(--border-subtle);
}

.qa__badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--teal-50);
  color: var(--teal-600);
  font-size: 11px;
  font-weight: 500;
}

.qa__title {
  margin-top: var(--space-4);
  font-size: var(--fs-2xl);
  letter-spacing: -0.02em;
}

.qa__desc {
  margin-top: 10px;
  max-width: 560px;
  font-size: var(--fs-sm);
  line-height: var(--lh-loose);
  color: var(--text-muted);
}

.qa__search {
  width: 280px;
  flex: none;
}

.qa__body {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: var(--space-8);
  margin-top: var(--space-8);
}

.qa__side-title {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
  letter-spacing: 0.04em;
  margin-bottom: var(--space-3);
}

.qa__cat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 12px;
  margin-bottom: 2px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--fs-sm);
  color: var(--text-body);
  cursor: pointer;
  text-align: left;

  &:hover {
    background: var(--ink-100);
  }

  &.is-active {
    background: var(--ink-900);
    color: #fff;
    font-weight: 500;
  }
}

.qa__cat-count {
  font-size: var(--fs-xs);
  opacity: 0.6;
}

.qa__side-card {
  margin-top: var(--space-6);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-surface);

  p {
    margin-top: 6px;
    font-size: var(--fs-xs);
    line-height: var(--lh-loose);
    color: var(--text-muted);
  }
}

.qa__side-card-title {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-strong);
}

.qa__side-btn {
  width: 100%;
  margin-top: var(--space-3);
}

.qa__main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qa__item {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  overflow: hidden;
  transition: border-color var(--dur-fast) var(--ease);

  &.is-open {
    border-color: var(--brand-200);
    box-shadow: var(--shadow-xs);
  }
}

.qa__q {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: var(--space-4) var(--space-5);
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.qa__cat-tag {
  flex: none;
  padding: 1px 7px;
  border-radius: var(--radius-xs);
  background: var(--ink-50);
  color: var(--text-muted);
  font-size: 11px;
}

.qa__q-text {
  flex: 1;
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--text-strong);
}

.qa__q-toggle {
  flex: none;
  width: 20px;
  text-align: center;
  font-size: 18px;
  color: var(--text-subtle);
  line-height: 1;
}

.qa__a {
  padding: 0 var(--space-5) var(--space-5);
}

.qa__a-text {
  padding-top: var(--space-3);
  border-top: 1px dashed var(--border-subtle);
  font-size: var(--fs-sm);
  line-height: 1.9;
  color: var(--text-body);
}

.qa__a-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.qa__source {
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.qa__more {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid var(--brand-200);
  border-radius: var(--radius-pill);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: var(--fs-xs);
  font-weight: 500;
  cursor: pointer;

  &:hover {
    background: var(--brand-100);
  }
}

.qa__empty {
  padding: var(--space-16);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-subtle);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-md);
}

@media (max-width: 900px) {
  .qa__head {
    flex-direction: column;
    align-items: flex-start;
  }
  .qa__search {
    width: 100%;
  }
  .qa__body {
    grid-template-columns: 1fr;
  }
}
</style>
