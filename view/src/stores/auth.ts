import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { principalToUser } from '@/api/auth'
import type { AuthUser, Tokens } from '@/api/types'
import {
  clearAuthStorage,
  getRefreshToken,
  getStoredUser,
  getToken,
  setStoredUser,
  setTokens
} from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const user = ref<AuthUser | null>(getStoredUser<AuthUser>())

  const isLoggedIn = computed(() => Boolean(token.value && user.value))
  const isStaff = computed(() => user.value?.subjectType === 'MERCHANT_STAFF')
  const isConsumer = computed(() => user.value?.subjectType === 'CONSUMER')
  const displayName = computed(() => user.value?.displayName || '未登录')

  /** 登录 / 续期成功后统一落库：令牌对进 storage，契约主体映射成 UI 模型 */
  function applyTokens(tokens: Tokens) {
    token.value = tokens.accessToken
    user.value = principalToUser(tokens.principal)
    setTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAt)
    setStoredUser(user.value)
  }

  async function loginBySms(phone: string, code: string) {
    const tokens = await authApi.loginBySms(phone, code)
    applyTokens(tokens)
    return tokens
  }

  async function loginByStaff(payload: { merchantCode: string; identifier: string; password: string }) {
    const tokens = await authApi.loginByStaff(payload)
    applyTokens(tokens)
    return tokens
  }

  /** 刷新页面后用令牌换回主体信息；401 会先被拦截器静默续期，续期失败才会走到这里重置 */
  async function loadMe() {
    if (!token.value) return null
    try {
      const principal = await authApi.fetchMe()
      user.value = principalToUser(principal)
      setStoredUser(user.value)
      return user.value
    } catch {
      reset()
      return null
    }
  }

  function reset() {
    token.value = ''
    user.value = null
    clearAuthStorage()
  }

  async function signOut() {
    try {
      if (token.value) await authApi.logout(getRefreshToken() || undefined)
    } finally {
      reset()
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    isStaff,
    isConsumer,
    displayName,
    loginBySms,
    loginByStaff,
    loadMe,
    signOut,
    reset
  }
})
