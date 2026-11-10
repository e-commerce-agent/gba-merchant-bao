import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  }
})

const APP_TITLE = import.meta.env.VITE_APP_TITLE || '粤港澳商户宝'

router.beforeEach((to) => {
  const auth = useAuthStore()
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)
  const subjectType = to.matched.find((r) => r.meta.subjectType)?.meta.subjectType

  if (requiresAuth && !auth.isLoggedIn) {
    const loginPath = subjectType === 'MERCHANT_STAFF' ? '/merchant/login' : '/login'
    return { path: loginPath, query: { redirect: to.fullPath } }
  }

  // 已登录但走错端：消费者访问商户端（或反向），送回各自首页
  if (requiresAuth && auth.isLoggedIn && subjectType && auth.user?.subjectType !== subjectType) {
    return auth.user?.subjectType === 'MERCHANT_STAFF' ? '/merchant/dashboard' : '/consumer/chat'
  }

  // 已登录再访问登录页，直接进各自工作区
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return auth.user?.subjectType === 'MERCHANT_STAFF' ? '/merchant/dashboard' : '/consumer/chat'
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · ${APP_TITLE}` : APP_TITLE
})

export default router
