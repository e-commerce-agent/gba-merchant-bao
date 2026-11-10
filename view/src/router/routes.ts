import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由按业务入口分组：/public 公开商城、/consumer 消费者中心、/merchant 商户工作台。
 * /platform 平台管理后台本期不实现（2.2.4 已明确为后续多租户 SaaS 扩展角色）。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/PublicLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/public/HomeView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'products',
        name: 'product-list',
        component: () => import('@/views/public/ProductListView.vue'),
        meta: { title: '全部商品' }
      },
      {
        path: 'products/:id',
        name: 'product-detail',
        component: () => import('@/views/public/ProductDetailView.vue'),
        meta: { title: '商品详情' }
      },
      {
        path: 'qa',
        name: 'public-qa',
        component: () => import('@/views/public/QaView.vue'),
        meta: { title: '公开知识问答' }
      }
    ]
  },
  {
    // 店铺入口：/store/{storeSlug} 由 URL 公开带定租户（后端契约确认），落库后进公开商城
    path: '/store/:slug',
    redirect: (to) => {
      const slug = String(to.params.slug || '')
      if (slug) localStorage.setItem('gmb.store_slug', slug)
      return { path: '/products' }
    }
  },
  {
    path: '/login',
    name: 'consumer-login',
    component: () => import('@/views/auth/ConsumerLoginView.vue'),
    meta: { title: '消费者登录', guestOnly: true }
  },
  {
    path: '/merchant/login',
    name: 'merchant-login',
    component: () => import('@/views/auth/MerchantLoginView.vue'),
    meta: { title: '商户员工登录', guestOnly: true }
  },
  {
    path: '/consumer',
    component: () => import('@/layouts/ConsoleLayout.vue'),
    meta: { requiresAuth: true, subjectType: 'CONSUMER' },
    children: [
      { path: '', redirect: '/consumer/chat' },
      {
        path: 'chat/:id?',
        name: 'consumer-chat',
        component: () => import('@/views/consumer/ChatView.vue'),
        meta: { title: '智能客服', requiresAuth: true, subjectType: 'CONSUMER' }
      },
      {
        path: 'orders',
        name: 'consumer-orders',
        component: () => import('@/views/consumer/OrdersView.vue'),
        meta: { title: '我的订单', requiresAuth: true, subjectType: 'CONSUMER' }
      },
      {
        path: 'sessions',
        name: 'consumer-sessions',
        component: () => import('@/views/consumer/SessionsView.vue'),
        meta: { title: '历史会话', requiresAuth: true, subjectType: 'CONSUMER' }
      }
    ]
  },
  {
    path: '/merchant',
    component: () => import('@/layouts/ConsoleLayout.vue'),
    meta: { requiresAuth: true, subjectType: 'MERCHANT_STAFF' },
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      {
        path: 'dashboard',
        name: 'merchant-dashboard',
        component: () => import('@/views/merchant/DashboardView.vue'),
        meta: { title: '工作台', requiresAuth: true, subjectType: 'MERCHANT_STAFF' }
      },
      {
        path: 'conversations',
        name: 'merchant-conversations',
        component: () => import('@/views/merchant/ConversationsView.vue'),
        meta: { title: '会话工作台', requiresAuth: true, subjectType: 'MERCHANT_STAFF' }
      },
      {
        path: 'products',
        name: 'merchant-products',
        component: () => import('@/views/merchant/ProductsView.vue'),
        meta: { title: '商品与 SKU', requiresAuth: true, subjectType: 'MERCHANT_STAFF' }
      },
      {
        path: 'knowledge',
        name: 'merchant-knowledge',
        component: () => import('@/views/merchant/KnowledgeView.vue'),
        meta: { title: '知识库', requiresAuth: true, subjectType: 'MERCHANT_STAFF' }
      },
      {
        path: 'tasks',
        name: 'merchant-tasks',
        component: () => import('@/views/merchant/TasksView.vue'),
        meta: { title: '文档任务', requiresAuth: true, subjectType: 'MERCHANT_STAFF' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]
