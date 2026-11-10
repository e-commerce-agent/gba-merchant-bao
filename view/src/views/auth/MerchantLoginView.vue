<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, OfficeBuilding, User } from '@element-plus/icons-vue'
import AuthShell from '@/components/AuthShell.vue'
import { useAuthStore } from '@/stores/auth'
import { USE_MOCK } from '@/api/request'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  merchantCode: USE_MOCK ? 'M_3C_DEMO' : '',
  account: '',
  password: ''
})

const rules: FormRules = {
  merchantCode: [{ required: true, message: '请输入商户编码', trigger: 'blur' }],
  account: [{ required: true, message: '请输入用户名或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await auth.loginByStaff({
      merchantCode: form.merchantCode,
      identifier: form.account,
      password: form.password
    })
    ElMessage.success('登录成功')
    const redirect = String(route.query.redirect || '/merchant/dashboard')
    router.replace(redirect)
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败')
  } finally {
    submitting.value = false
  }
}

function fillDemo() {
  form.merchantCode = 'M_3C_DEMO'
  form.account = 'admin'
  form.password = 'Admin@123'
}
</script>

<template>
  <AuthShell
    tone="merchant"
    eyebrow="商户客服工作台"
    headline="一个工作台，看住所有会话。"
    description="查看 Agent 与消费者的对话、接管低置信度会话、维护商品与知识库、跟踪文档解析任务。"
    :points="[
      '商户编码 + 账号 + 密码，BCrypt 校验',
      '只看本商户的会话、商品和文档，租户隔离',
      '低置信度会话自动进入待接管队列'
    ]"
  >
    <div class="login">
      <h2 class="login__title">商户员工登录</h2>
      <p class="login__sub">首期商户管理员与客服为同一角色</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="商户编码" prop="merchantCode">
          <el-input
            v-model="form.merchantCode"
            size="large"
            placeholder="例如 M_3C_DEMO"
            :prefix-icon="OfficeBuilding"
          />
        </el-form-item>

        <el-form-item label="用户名 / 手机号" prop="account">
          <el-input
            v-model="form.account"
            size="large"
            placeholder="请输入用户名或手机号"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            show-password
            placeholder="请输入密码"
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
        </el-form-item>

        <div class="login__row">
          <el-checkbox>记住登录状态</el-checkbox>
          <button type="button" class="login__forget" @click="ElMessage.info('请联系平台管理员重置密码')">
            忘记密码？
          </button>
        </div>

        <el-button
          type="primary"
          size="large"
          class="login__submit"
          :loading="submitting"
          @click="submit"
        >
          进入工作台
        </el-button>
      </el-form>

      <div v-if="USE_MOCK" class="login__demo">
        <div>
          <span class="login__demo-title">演示账号</span>
          M_3C_DEMO · admin · Admin@123
        </div>
        <button type="button" class="login__demo-btn" @click="fillDemo">一键填入</button>
      </div>

      <div class="login__switch">
        我是消费者
        <RouterLink to="/login">去消费者登录</RouterLink>
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

.login__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: calc(var(--space-2) * -1) 0 var(--space-4);
}

.login__forget {
  padding: 0;
  border: none;
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--text-muted);
  cursor: pointer;

  &:hover {
    color: var(--brand-600);
  }
}

.login__submit {
  width: 100%;
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
  font-family: var(--font-mono);
}

.login__demo-title {
  display: block;
  font-family: var(--font-sans);
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
  font-family: var(--font-sans);
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
