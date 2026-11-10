<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, User, ArrowDown } from '@element-plus/icons-vue'
import BrandMark from './BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const keyword = ref('')

const navs = [
  { label: '首页', to: '/' },
  { label: '全部商品', to: '/products' },
  { label: '知识问答', to: '/qa' }
]

const activePath = computed(() => {
  if (route.path.startsWith('/products')) return '/products'
  if (route.path.startsWith('/qa')) return '/qa'
  return '/'
})

function onSearch() {
  const q = keyword.value.trim()
  router.push({ path: '/products', query: q ? { keyword: q } : {} })
}

async function signOut() {
  await auth.signOut()
  router.push('/')
}
</script>

<template>
  <header class="ph">
    <div class="container ph__inner">
      <RouterLink to="/" class="ph__brand">
        <BrandMark />
      </RouterLink>

      <nav class="ph__nav">
        <RouterLink
          v-for="item in navs"
          :key="item.to"
          :to="item.to"
          class="ph__link"
          :class="{ 'is-active': activePath === item.to }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="ph__search">
        <el-input
          v-model="keyword"
          placeholder="搜索充电器、数据线、型号…"
          :prefix-icon="Search"
          clearable
          @keyup.enter="onSearch"
        />
      </div>

      <div class="ph__actions">
        <RouterLink to="/merchant/login" class="ph__staff">商户入口</RouterLink>

        <el-dropdown v-if="auth.isLoggedIn" trigger="click">
          <button class="ph__user" type="button">
            <el-avatar :size="28" class="ph__avatar">
              {{ auth.displayName.slice(0, 1) }}
            </el-avatar>
            <span class="ph__username">{{ auth.displayName }}</span>
            <el-icon :size="12"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/consumer/chat')">我的对话</el-dropdown-item>
              <el-dropdown-item @click="router.push('/consumer/orders')">我的订单</el-dropdown-item>
              <el-dropdown-item divided @click="signOut">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button v-else type="primary" :icon="User" @click="router.push('/login')">
          登录
        </el-button>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
.ph {
  position: sticky;
  top: 0;
  z-index: 50;
  height: var(--header-h);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: saturate(150%) blur(12px);
  border-bottom: 1px solid var(--border-subtle);
}

.ph__inner {
  display: flex;
  align-items: center;
  height: 100%;
  gap: var(--space-8);
}

.ph__brand {
  flex: none;
}

.ph__nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.ph__link {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-base);
  color: var(--text-muted);
  transition: color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);

  &:hover {
    color: var(--text-strong);
    background: var(--ink-50);
  }

  &.is-active {
    color: var(--brand-600);
    font-weight: 500;
    background: var(--brand-50);
  }
}

.ph__search {
  flex: 1;
  max-width: 320px;
  margin-left: auto;
}

.ph__actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex: none;
}

.ph__staff {
  font-size: var(--fs-sm);
  color: var(--text-muted);

  &:hover {
    color: var(--brand-600);
  }
}

.ph__user {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 10px 0 4px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-pill);
  background: var(--bg-surface);
  cursor: pointer;
  color: var(--text-body);

  &:hover {
    border-color: var(--border-default);
  }
}

.ph__avatar {
  background: var(--brand-500);
  font-size: 12px;
  font-weight: 500;
}

.ph__username {
  font-size: var(--fs-sm);
  max-width: 84px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1080px) {
  .ph__search {
    display: none;
  }
  .ph__nav {
    margin-left: auto;
  }
}

@media (max-width: 720px) {
  .ph__nav,
  .ph__staff,
  .ph__username {
    display: none;
  }
  .ph__actions {
    margin-left: auto;
  }
}
</style>
