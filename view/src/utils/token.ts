/**
 * 本地会话存储。
 * 只存后端下发的 Bearer token / refresh token 与当前主体信息；不存密码、不存验证码、不存任何密钥。
 * refresh token 仅用于静默续期，续期由后端 /auth/refresh 完成轮换。
 */

const TOKEN_KEY = 'gmb.access_token'
const REFRESH_KEY = 'gmb.refresh_token'
const EXPIRES_KEY = 'gmb.access_expires_at'
const USER_KEY = 'gmb.auth_user'
/** token 版本号：任一标签页换发新令牌都会推进，供本标签页丢弃过期的续期响应 */
const EPOCH_KEY = 'gmb.token_epoch'
/** 登出广播 key：storage 事件只在「其他」标签页触发，用于同类型 tabs 的登出同步 */
const LOGOUT_BROADCAST_KEY = 'gmb.logout_broadcast'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_KEY) || ''
}

/** UTC date-time 字符串，用于请求前预判过期、提前续期 */
export function getAccessExpiresAt(): string {
  return localStorage.getItem(EXPIRES_KEY) || ''
}

export function setTokens(accessToken: string, refreshToken: string, expiresAt?: string): void {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
  if (expiresAt) {
    localStorage.setItem(EXPIRES_KEY, expiresAt)
  }
  bumpEpoch()
}

/** 当前 token 版本号。续期发起前记录、成功后比对，不一致说明另一标签页已换发，丢弃本次响应 */
export function getTokenEpoch(): number {
  return Number(localStorage.getItem(EPOCH_KEY) || 0)
}

function bumpEpoch(): void {
  localStorage.setItem(EPOCH_KEY, String(Date.now()))
}

export function getStoredUser<T>(): T | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function setStoredUser(user: unknown): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuthStorage(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(EXPIRES_KEY)
  localStorage.removeItem(USER_KEY)
  bumpEpoch()
}

/** 本标签页登出时，向其他同类型标签页广播（storage 事件只触发于「其他」页面） */
export function broadcastLogout(): void {
  localStorage.setItem(LOGOUT_BROADCAST_KEY, String(Date.now()))
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key !== LOGOUT_BROADCAST_KEY || !e.newValue) return
    if (getToken()) clearAuthStorage()
    if (!location.pathname.includes('/login')) {
      location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
    }
  })
}
