<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Iphone, Message } from '@element-plus/icons-vue'
import AuthShell from '@/components/AuthShell.vue'
import { sendSmsCode } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { USE_MOCK } from '@/api/request'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer: number | undefined

const form = reactive({ phone: '', code: '' })

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请先填写正确的手机号')
    return
  }
  sending.value = true
  try {
    const res = await sendSmsCode(form.phone)
    // 契约：retryAfter 为可重发倒计时；验证码本身 300 秒有效
    countdown.value = res.retryAfter || 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
    // 联调环境（dev mock 显式开启）后端会回传 debugCode，自动回填省一次输入
    if (res.debugCode && !form.code) {
      form.code = res.debugCode
      ElMessage.success('验证码已发送，已自动回填（联调环境）')
    } else {
      ElMessage.success('验证码已发送，请查收短信')
    }
  } catch (e) {
    const err = e as Error & { retryAfter?: number }
    // 429 限流：用 Retry-After 驱动倒计时，避免用户反复点
    if (err.retryAfter && err.retryAfter > 0) {
      countdown.value = err.retryAfter
      timer = window.setInterval(() => {
        countdown.value -= 1
        if (countdown.value <= 0) window.clearInterval(timer)
      }, 1000)
    }
    ElMessage.error(err.message || '发送失败')
  } finally {
    sending.value = false
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await auth.loginBySms(form.phone, form.code)
    ElMessage.success('登录成功')
    const redirect = String(route.query.redirect || '/consumer/chat')
    router.replace(redirect)
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败')
  } finally {
    submitting.value = false
  }
}

function fillDemo() {
  // 后端已确认：验证码每次随机生成（不固定 123456），dev mock 环境发送后会通过
  // debugCode 自动回填，因此一键填入只填手机号
  form.phone = '13800138000'
  form.code = ''
}

onUnmounted(() => timer && window.clearInterval(timer))
</script>

<template>
  <AuthShell
    tone="consumer"
    eyebrow="消费者中心"
    headline="登录后，问题和订单都在一起。"
    description="验证码登录，不需要记密码。登录后可查看自己的订单、物流，以及与 Agent 的历史对话。"
    :points="[
      '手机号 + 短信验证码，60 秒可重发',
      '只返回你自己的订单与物流，跨账号不可见',
      '历史会话保存在服务端，换设备也能接着聊'
    ]"
  >
    <div class="login">
      <h2 class="login__title">消费者登录</h2>
      <p class="login__sub">未注册的手机号验证通过后将自动创建账号</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            size="large"
            placeholder="请输入 11 位手机号"
            :prefix-icon="Iphone"
            maxlength="11"
          />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="login__code">
            <el-input
              v-model="form.code"
              size="large"
              placeholder="6 位验证码"
              :prefix-icon="Message"
              maxlength="6"
              @keyup.enter="submit"
            />
            <el-button
              size="large"
              class="login__code-btn"
              :disabled="countdown > 0"
              :loading="sending"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="login__submit"
          :loading="submitting"
          @click="submit"
        >
          登录 / 注册
        </el-button>
      </el-form>

      <div v-if="USE_MOCK" class="login__demo">
        <div>
          <span class="login__demo-title">演示账号</span>
          手机号 13800138000 · 验证码发送后自动回填
        </div>
        <button type="button" class="login__demo-btn" @click="fillDemo">一键填入</button>
      </div>

      <div class="login__switch">
        商户员工？
        <RouterLink to="/merchant/login">去商户登录</RouterLink>
      </div>
    </div>
  </AuthShell>
</template>

<style scoped lang="scss">
.login__title {
  font-size: var(--fs-xl);
  letter-spacing: -0.01em;
}

.login__sub {
  margin-top: 6px;
  margin-bottom: var(--space-6);
  font-size: var(--fs-xs);
  color: var(--text-subtle);
}

.login__code {
  display: flex;
  gap: 8px;
  width: 100%;
}

.login__code-btn {
  flex: none;
  width: 118px;
}

.login__submit {
  width: 100%;
  margin-top: var(--space-2);
}

.login__demo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding: 10px 12px;
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  font-size: 11.5px;
  color: var(--text-muted);
}

.login__demo-title {
  display: block;
  font-weight: 500;
  color: var(--text-body);
  margin-bottom: 2px;
}

.login__demo-btn {
  flex: none;
  padding: 4px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  background: var(--bg-surface);
  font-size: 11.5px;
  color: var(--text-body);
  cursor: pointer;

  &:hover {
    border-color: var(--brand-300);
    color: var(--brand-600);
  }
}

.login__switch {
  margin-top: var(--space-6);
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--text-subtle);

  a {
    color: var(--brand-600);
    margin-left: 4px;
  }
}
</style>
