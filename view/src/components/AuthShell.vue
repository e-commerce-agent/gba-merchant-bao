<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'

withDefaults(
  defineProps<{
    eyebrow?: string
    headline?: string
    description?: string
    points?: string[]
    tone?: 'consumer' | 'merchant'
  }>(),
  {
    eyebrow: '粤港澳商户宝',
    headline: '',
    description: '',
    points: () => [],
    tone: 'consumer'
  }
)
</script>

<template>
  <div class="auth" :class="`is-${tone}`">
    <aside class="auth__aside">
      <RouterLink to="/" class="auth__brand">
        <BrandMark />
      </RouterLink>

      <div class="auth__copy">
        <span class="auth__eyebrow">{{ eyebrow }}</span>
        <h1 class="auth__headline">{{ headline }}</h1>
        <p class="auth__desc">{{ description }}</p>

        <ul v-if="points.length" class="auth__points">
          <li v-for="p in points" :key="p">
            <i />
            {{ p }}
          </li>
        </ul>
      </div>

      <p class="auth__foot">教学演示环境 · 样板租户 M_3C_DEMO</p>
    </aside>

    <main class="auth__main">
      <div class="auth__panel">
        <slot />
      </div>
      <RouterLink to="/" class="auth__back">← 返回商城首页</RouterLink>
    </main>
  </div>
</template>

<style scoped lang="scss">
.auth {
  display: grid;
  grid-template-columns: 45% 55%;
  min-height: 100vh;
  background: var(--bg-page);
}

.auth__aside {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: var(--space-10) var(--space-12);
  background: var(--ink-900);
  color: #fff;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: radial-gradient(rgba(255, 255, 255, 0.14) 1px, transparent 1px);
    background-size: 26px 26px;
    mask-image: radial-gradient(600px 400px at 30% 15%, rgba(0, 0, 0, 0.9), transparent 70%);
    pointer-events: none;
  }
}

.is-merchant .auth__aside {
  background: #101a33;
}

.auth__brand {
  position: relative;
  display: inline-flex;

  :deep(.brand__name) {
    color: #fff;
  }

  :deep(.brand__sub) {
    color: rgba(255, 255, 255, 0.45);
  }
}

.auth__copy {
  position: relative;
  margin-top: auto;
  padding-bottom: var(--space-10);
}

.auth__eyebrow {
  display: inline-block;
  padding: 4px 10px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: var(--radius-pill);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: rgba(255, 255, 255, 0.7);
}

.auth__headline {
  margin-top: var(--space-5);
  font-size: 34px;
  line-height: 1.3;
  color: #fff;
  letter-spacing: -0.02em;
  max-width: 460px;
}

.auth__desc {
  margin-top: var(--space-4);
  max-width: 420px;
  font-size: var(--fs-sm);
  line-height: var(--lh-loose);
  color: rgba(255, 255, 255, 0.55);
}

.auth__points {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: var(--space-6);

  li {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    font-size: var(--fs-sm);
    color: rgba(255, 255, 255, 0.72);
  }

  i {
    width: 5px;
    height: 5px;
    margin-top: 9px;
    border-radius: 50%;
    background: var(--teal-500);
    flex: none;
  }
}

.auth__foot {
  position: relative;
  padding-top: var(--space-5);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: var(--fs-xs);
  color: rgba(255, 255, 255, 0.32);
  font-variant-numeric: tabular-nums;
}

.auth__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-6);
}

.auth__panel {
  width: 100%;
  max-width: 380px;
}

.auth__back {
  margin-top: var(--space-8);
  font-size: var(--fs-xs);
  color: var(--text-subtle);

  &:hover {
    color: var(--brand-600);
  }
}

@media (max-width: 900px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .auth__aside {
    display: none;
  }
}
</style>
