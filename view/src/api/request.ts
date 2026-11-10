import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse, Tokens } from '@/api/types'
import {
  broadcastLogout,
  clearAuthStorage,
  getRefreshToken,
  getToken,
  getTokenEpoch,
  setTokens
} from '@/utils/token'
import { mockDispatch } from '@/mock'

/** 是否走本地 Mock。联调时把 .env.development 的 VITE_USE_MOCK 改成 false 即切真实接口 */
export const USE_MOCK = String(import.meta.env.VITE_USE_MOCK) === 'true'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export class ApiError extends Error {
  code: number
  status: number
  requestId?: string

  constructor(message: string, code: number, status = 0, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.requestId = requestId
  }
}

function newRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

/** 统一响应体的成功判定：后端已确认 HTTP 200/201 + 业务码 code=0（员工创建 201，其余 200） */
function isSuccessCode(code: number): boolean {
  return code === 0
}

/** 契约错误码 → 用户可读文案（正文有 message 时优先用后端的） */
const CODE_MESSAGES: Record<number, string> = {
  10001: '请求参数无效',
  10002: '登录状态已失效，请重新登录',
  10003: '没有权限执行此操作',
  10004: '资源不存在或无权访问',
  10009: '用户名或手机号已被使用',
  10500: '服务内部错误，请稍后重试',
  11001: '账号或密码错误',
  11002: '验证码错误或已过期',
  11003: '请求过于频繁，请稍后再试',
  11501: '依赖服务暂不可用，请稍后重试',
  11502: '短信服务未开启，请联系管理员'
}

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    // 契约 securityScheme：HTTP Bearer (JWT)
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  // 响应头 X-Request-Id 与 body.requestId 一致，便于联调对账
  if (!config.headers.get('X-Request-Id')) {
    config.headers.set('X-Request-Id', newRequestId())
  }
  return config
})

/* ------------------------------ 401 静默续期 ------------------------------ */

let refreshing: Promise<boolean> | null = null
let refreshRequestId = ''

/** 用 refreshToken 换新令牌对。契约要求：同一次续期尝试重试时必须复用同一个 refreshRequestId */
async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  if (!refreshRequestId) refreshRequestId = newRequestId()
  // 记录发起时的 token 版本号：若续期期间另一标签页已换发新令牌，丢弃本次响应（契约交付记录的 epoch 约定）
  const epochBefore = getTokenEpoch()
  try {
    // 走独立实例：不注入可能已失效的 access token，不经过统一拦截器
    const res = await axios.post<ApiResponse<Tokens>>(
      `${BASE_URL}/v1/auth/refresh`,
      { refreshToken, refreshRequestId },
      {
        timeout: 20000,
        headers: { 'Content-Type': 'application/json', 'X-Request-Id': refreshRequestId }
      }
    )
    const body = res.data
    if (body && isSuccessCode(body.code) && body.data?.accessToken) {
      if (getTokenEpoch() !== epochBefore) {
        // 另一标签页已换发更新令牌，保留本地存储里的新版，不覆盖
        return true
      }
      setTokens(body.data.accessToken, body.data.refreshToken, body.data.expiresAt)
      return true
    }
    return false
  } catch {
    return false
  } finally {
    refreshRequestId = ''
  }
}

function kickToLogin(): void {
  const hadToken = Boolean(getToken())
  clearAuthStorage()
  if (hadToken) broadcastLogout()
  ElMessage.warning('登录状态已失效，请重新登录')
  const redirect = encodeURIComponent(location.pathname + location.search)
  if (!location.pathname.includes('/login')) {
    location.href = `/login?redirect=${redirect}`
  }
}

/** 401 时先尝试续期并重放原请求；续期失败或重放仍 401 才踢回登录页 */
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined
    // 非统一响应体（文件下载、SSE 等）直接透传
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return response.data
    }
    if (!isSuccessCode(body.code)) {
      throw new ApiError(body.message || '请求失败', body.code, response.status, body.requestId)
    }
    return body.data
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data

    if (status === 401) {
      const original = error.config as (InternalAxiosRequestConfig & { __retried?: boolean }) | undefined
      const url = original?.url || ''
      const isAuthCall = url.includes('/auth/refresh') || url.includes('/auth/login') || url.includes('/auth/logout')

      if (original && !original.__retried && !isAuthCall && !USE_MOCK) {
        original.__retried = true
        // 并发 401 共享同一次续期，避免多个请求同时打 /auth/refresh
        if (!refreshing) {
          refreshing = doRefresh().finally(() => {
            refreshing = null
          })
        }
        const ok = await refreshing
        if (ok) {
          return http.request(original)
        }
      }

      kickToLogin()
      return Promise.reject(
        new ApiError(body?.message || '登录状态已失效', body?.code ?? 10002, status, body?.requestId)
      )
    }

    const fallback = CODE_MESSAGES[body?.code ?? -1]
    const message =
      body?.message ||
      fallback ||
      (status === 0 ? '网络异常，请确认后端已启动' : `请求失败（${status}）`)

    // 429 + Retry-After：把剩余秒数挂到错误对象上，供短信倒计时使用
    if (status === 429) {
      const retryAfter = Number(error.response?.headers?.['retry-after'] || 0)
      const err = new ApiError(message, body?.code ?? 11003, status, body?.requestId)
      ;(err as ApiError & { retryAfter?: number }).retryAfter = retryAfter
      return Promise.reject(err)
    }

    return Promise.reject(new ApiError(message, body?.code ?? status, status, body?.requestId))
  }
)

interface MockableConfig {
  url: string
  method: string
  data?: unknown
  params?: Record<string, unknown>
}

/** 统一入口：Mock 与真实接口走同一签名，页面代码无需感知切换 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  if (USE_MOCK) {
    const payload: MockableConfig = {
      url: config.url || '',
      method: (config.method || 'get').toUpperCase(),
      data: config.data,
      params: config.params as Record<string, unknown> | undefined
    }
    return mockDispatch(payload) as Promise<T>
  }
  return http.request(config) as unknown as Promise<T>
}

export const get = <T>(url: string, params?: object) => request<T>({ url, method: 'get', params })

export const post = <T>(url: string, data?: unknown) => request<T>({ url, method: 'post', data })

export const put = <T>(url: string, data?: unknown) => request<T>({ url, method: 'put', data })

export const del = <T>(url: string, data?: unknown) =>
  request<T>({ url, method: 'delete', data })
