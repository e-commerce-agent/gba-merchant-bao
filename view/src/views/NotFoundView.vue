<script setup lang="ts">
import { useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
</script>

<template>
  <div class="nf">
    <header class="nf__header">
      <RouterLink to="/"><BrandMark /></RouterLink>
    </header>

    <main class="nf__main">
      <div class="nf__code num">404</div>
      <h1 class="nf__title">这个页面不存在</h1>
      <p class="nf__desc">链接可能已经失效，或者你没有访问该页面的权限。</p>

      <div class="nf__actions">
        <el-button type="primary" @click="router.push('/')">回到商城首页</el-button>
        <el-button @click="router.push('/consumer/chat')">去问 Agent</el-button>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.nf {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-page);
}

.nf__header {
  padding: var(--space-6) var(--space-10);
}

.nf__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 0 var(--space-6) var(--space-20);
}

.nf__code {
  font-size: 96px;
  font-weight: 600;
  letter-spacing: -0.04em;
  line-height: 1;
  color: var(--brand-100);
}

.nf__title {
  margin-top: var(--space-4);
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.nf__desc {
  margin-top: 8px;
  font-size: var(--fs-sm);
  color: var(--text-muted);
}

.nf__actions {
  display: flex;
  gap: 10px;
  margin-top: var(--space-6);
}
</style>
