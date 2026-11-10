<script setup lang="ts">
import BrandMark from './BrandMark.vue'

const columns = [
  {
    title: '商品',
    links: ['充电器', '数据线', '移动电源', '耳机', '车载充电']
  },
  {
    title: '服务',
    links: ['智能客服', '物流查询', '售后政策', '常见问题']
  },
  {
    title: '商户',
    links: ['商户登录', '商品管理', '知识库', '文档任务']
  }
]
</script>

<template>
  <footer class="pf">
    <div class="container pf__inner">
      <div class="pf__brand">
        <BrandMark />
        <p class="pf__desc">
          面向粤港澳中小 3C 配件电商商户的智能客服 Agent 平台，
          把商品、库存、订单、物流与知识库连成一条可回答的链路。
        </p>
      </div>

      <div v-for="col in columns" :key="col.title" class="pf__col">
        <h4 class="pf__col-title">{{ col.title }}</h4>
        <ul class="pf__list">
          <li v-for="link in col.links" :key="link">
            <span class="pf__link">{{ link }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="container pf__bottom">
      <span>© 2026 粤港澳商户宝 · 软件综合项目实训作品</span>
      <span class="pf__meta">样板租户 M_3C_DEMO · 数据仅用于教学演示</span>
    </div>
  </footer>
</template>

<style scoped lang="scss">
.pf {
  margin-top: var(--space-20);
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-surface);
}

.pf__inner {
  display: grid;
  grid-template-columns: 1.6fr repeat(3, 1fr);
  gap: var(--space-8);
  padding-top: var(--space-12);
  padding-bottom: var(--space-10);
}

.pf__desc {
  margin-top: var(--space-4);
  max-width: 340px;
  font-size: var(--fs-sm);
  line-height: var(--lh-loose);
  color: var(--text-muted);
}

.pf__col-title {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-strong);
  margin-bottom: var(--space-3);
}

.pf__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pf__link {
  font-size: var(--fs-sm);
  color: var(--text-muted);
  cursor: default;

  &:hover {
    color: var(--brand-600);
  }
}

.pf__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding-top: var(--space-5);
  padding-bottom: var(--space-6);
  border-top: 1px solid var(--border-subtle);
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.pf__meta {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 860px) {
  .pf__inner {
    grid-template-columns: 1fr 1fr;
  }
  .pf__bottom {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
