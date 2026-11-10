<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChatDotRound,
  ChatLineSquare,
  Clock,
  Collection,
  DataBoard,
  Files,
  Fold,
  Goods,
  Expand,
  Shop,
  Tickets
} from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { USE_MOCK } from '@/api/request'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const auth = useAuthStore()

const isMerchant = computed(() => auth.user?.subjectType === 'MERCHANT_STAFF')

const menus = computed(() =>
  isMerchant.value
    ? [
        { label: '工作台', to: '/merchant/dashboard', icon: DataBoard },
        { label: '会话工作台', to: '/merchant/conversations', icon: ChatLineSquare },
        { label: '商品与 SKU', to: '/merchant/products', icon: Goods },
        { label: '知识库', to: '/merchant/knowledge', icon: Collection },
        { label: '文档任务', to: '/merchant/tasks', icon: Files }
      ]
    : [
        { label: '智能客服', to: '/consumer/chat', icon: ChatDotRound },
        { label: '我的订单', to: '/consumer/orders', icon: Tickets },
        { label: '历史会话', to: '/consumer/sessions', icon: Clock }
      ]
)

const pageTitle = computed(() => (route.meta.title as string) || '')

async function signOut() {
  await auth.signOut()
  router.push(isMerchant.value ? '/merchant/login' : '/')
}
</script>

<template>
  <div class="console" :class="{ 'is-collapsed': app.sidebarCollapsed }">
    <aside class="console__side">
      <div class="console__brand">
        <RouterLink :to="isMerchant ? '/merchant/dashboard' : '/consumer/chat'">
          <BrandMark :compact="app.sidebarCollapsed" :size="34" />
        </RouterLink>
      </div>

      <div v-if="!app.sidebarCollapsed" class="console__scope">
        <span class="console__scope-dot" :class="isMerchant ? 'is-staff' : 'is-consumer'" />
        {{ isMerchant ? auth.user?.merchantName || '商户工作台' : '消费者中心' }}
      </div>

      <nav class="console__nav">
        <RouterLink
          v-for="item in menus"
          :key="item.to"
          :to="item.to"
          class="console__item"
          :class="{ 'is-active': route.path.startsWith(item.to) }"
          :title="item.label"
        >
          <el-icon :size="17"><component :is="item.icon" /></el-icon>
          <span v-if="!app.sidebarCollapsed" class="console__item-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="console__side-foot">
        <RouterLink to="/" class="console__item console__item--ghost">
          <el-icon :size="17"><Shop /></el-icon>
          <span v-if="!app.sidebarCollapsed" class="console__item-label">回到商城</span>
        </RouterLink>

        <button class="console__collapse" type="button" @click="app.toggleSidebar()">
          <el-icon :size="16">
            <Expand v-if="app.sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
          <span v-if="!app.sidebarCollapsed">收起菜单</span>
        </button>
      </div>
    </aside>

    <div class="console__body">
      <header class="console__top">
        <div class="console__title">
          <h1>{{ pageTitle }}</h1>
          <span v-if="USE_MOCK" class="console__mock">MOCK 数据</span>
        </div>

        <div class="console__top-actions">
          <span class="console__tenant num">
            {{ isMerchant ? auth.user?.merchantCode : '消费者' }}
          </span>
          <el-dropdown trigger="click">
            <button class="console__user" type="button">
              <el-avatar :size="28" class="console__avatar">
                {{ auth.displayName.slice(0, 1) }}
              </el-avatar>
              <span class="console__username">{{ auth.displayName }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ isMerchant ? '商户员工' : '消费者' }} · {{ auth.user?.phone }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="signOut">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="console__main">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.console {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-page);
}

.console__side {
  display: flex;
  flex-direction: column;
  width: var(--sidebar-w);
  flex: none;
  padding: var(--space-4) 0 var(--space-3);
  background: var(--bg-surface);
  border-right: 1px solid var(--border-subtle);
  transition: width var(--dur) var(--ease);
}

.is-collapsed .console__side {
  width: var(--sidebar-w-collapsed);
  align-items: center;
}

.console__brand {
  padding: 0 var(--space-5) var(--space-5);
}

.is-collapsed .console__brand {
  padding: 0 0 var(--space-4);
}

.console__scope {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 var(--space-5) var(--space-3);
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  background: var(--ink-50);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.console__scope-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;

  &.is-staff {
    background: var(--brand-500);
  }

  &.is-consumer {
    background: var(--teal-500);
  }
}

.console__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 var(--space-3);
  flex: 1;
  overflow-y: auto;
}

.is-collapsed .console__nav {
  padding: 0;
  width: 100%;
  align-items: center;
}

.console__item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-base);
  color: var(--text-body);
  transition:
    background var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);

  &:hover {
    background: var(--ink-50);
    color: var(--text-strong);
  }

  &.is-active {
    background: var(--brand-50);
    color: var(--brand-600);
    font-weight: 500;

    .console__item-label {
      font-weight: 500;
    }
  }
}

.is-collapsed .console__item {
  width: 40px;
  justify-content: center;
  padding: 0;
}

.console__item--ghost {
  color: var(--text-muted);
}

.console__side-foot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-3);
  border-top: 1px solid var(--border-subtle);
}

.is-collapsed .console__side-foot {
  padding: var(--space-3) 0;
  align-items: center;
  width: 100%;
}

.console__collapse {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--text-subtle);
  cursor: pointer;

  &:hover {
    background: var(--ink-50);
    color: var(--text-body);
  }
}

.console__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.console__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  height: var(--topbar-h);
  flex: none;
  padding: 0 var(--space-6);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
}

.console__title {
  display: flex;
  align-items: center;
  gap: 10px;

  h1 {
    font-size: var(--fs-lg);
    font-weight: 600;
    letter-spacing: -0.01em;
  }
}

.console__mock {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--amber-50);
  color: var(--amber-600);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.console__top-actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.console__tenant {
  padding: 4px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-pill);
  font-size: var(--fs-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.console__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  cursor: pointer;

  &:hover {
    background: var(--ink-50);
  }
}

.console__avatar {
  background: var(--brand-500);
  font-size: 12px;
  font-weight: 500;
}

.console__username {
  font-size: var(--fs-sm);
  color: var(--text-body);
}

.console__main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-6);
}

@media (max-width: 900px) {
  .console__side {
    width: var(--sidebar-w-collapsed);
    align-items: center;
  }
  .console__scope,
  .console__item-label {
    display: none;
  }
  .console__item {
    width: 40px;
    justify-content: center;
    padding: 0;
  }
  .console__main {
    padding: var(--space-4);
  }
}
</style>
