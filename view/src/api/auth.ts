import { get, post } from './request'
import type { AuthUser, PrincipalView, SmsResult, Tokens } from './types'

/**
 * auth-v1.openapi.yaml 契约对接层。
 * 路径、字段名均与契约一致，不在前端拼装任何令牌内容。
 */

/**
 * 消费者端登录路径带 {storeSlug} 路径参数（后端已确认行为：店铺由公开 URL 带定）。
 * 取值优先级：/store/:slug 入口写入的 localStorage > 环境变量默认值（首期单店演示）。
 */
function currentStoreSlug(): string {
  if (typeof localStorage !== 'undefined') {
    const fromUrl = localStorage.getItem('gmb.store_slug')
    if (fromUrl) return fromUrl
  }
  return import.meta.env.VITE_DEFAULT_MERCHANT_CODE || 'M_3C_DEMO'
}

const storeAuthBase = () => `/v1/stores/${encodeURIComponent(currentStoreSlug())}/auth`

/** 发送登录短信验证码：POST /stores/{storeSlug}/auth/sms-codes */
export function sendSmsCode(phone: string) {
  return post<SmsResult>(`${storeAuthBase()}/sms-codes`, { phone })
}

/** 消费者登录（首次自动建号）：POST /stores/{storeSlug}/auth/consumer/login */
export function loginBySms(phone: string, code: string) {
  return post<Tokens>(`${storeAuthBase()}/consumer/login`, { phone, code })
}

/** 商户员工登录：POST /auth/staff/login（契约字段为 identifier：用户名或手机号） */
export function loginByStaff(payload: { merchantCode: string; identifier: string; password: string }) {
  return post<Tokens>('/v1/auth/staff/login', payload)
}

/** 当前认证主体：GET /auth/me（Bearer，不推进 idle 计时） */
export function fetchMe() {
  return get<PrincipalView>('/v1/auth/me')
}

/** 登出：POST /auth/logout（可携带 refreshToken 撤销单会话；重复退出仍返回 200） */
export function logout(refreshToken?: string) {
  return post<unknown>('/v1/auth/logout', refreshToken ? { refreshToken } : undefined)
}

/** 契约主体 → 前端 UI 模型。masked 手机号直接透传展示 */
export function principalToUser(p: PrincipalView): AuthUser {
  return {
    subjectId: p.accountId,
    subjectType: p.role,
    displayName: p.displayName,
    phone: p.phoneMasked,
    merchantId: p.merchantId,
    merchantCode: p.merchantCode
  }
}
