<script setup lang="ts">
/**
 * 注册页
 *
 * 字段：username / email / password / confirmPassword
 * 校验：用户名 3-32 位字母数字下划线、邮箱合法、密码 ≥8 位含字母+数字、确认一致
 * 密码强度实时提示（弱/中/强）
 * 注册成功后跳转首页
 */
import { reactive, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElAvatar,
  ElIcon,
  ElProgress,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { User, Lock, Message, Key } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

/** 密码强度等级 */
type StrengthLevel = 0 | 1 | 2 | 3
interface StrengthInfo {
  level: StrengthLevel
  label: string
  color: string
  percentage: number
}

/** 计算密码强度：基于长度与字符种类 */
function calcStrength(password: string): StrengthInfo {
  if (!password) {
    return { level: 0, label: '', color: 'var(--color-gray-300)', percentage: 0 }
  }
  let kinds = 0
  if (/[a-z]/.test(password)) kinds++
  if (/[A-Z]/.test(password)) kinds++
  if (/\d/.test(password)) kinds++
  if (/[^a-zA-Z0-9]/.test(password)) kinds++

  const len = password.length
  let level: StrengthLevel = 0
  if (len >= 8 && kinds >= 2) level = 1
  if (len >= 10 && kinds >= 3) level = 2
  if (len >= 12 && kinds >= 3) level = 3

  const map: Record<StrengthLevel, StrengthInfo> = {
    0: { level: 0, label: '弱', color: 'var(--color-danger)', percentage: 25 },
    1: { level: 1, label: '中', color: 'var(--color-warning)', percentage: 50 },
    2: { level: 2, label: '强', color: 'var(--color-info)', percentage: 75 },
    3: { level: 3, label: '很强', color: 'var(--color-success)', percentage: 100 },
  }
  return map[level]
}

const strength = computed(() => calcStrength(form.password))

/** 校验规则 */
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,32}$/,
      message: '3-32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error('请输入密码'))
        if (value.length < 8) return callback(new Error('密码至少 8 位'))
        if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
          return callback(new Error('密码需同时包含字母和数字'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error('请再次输入密码'))
        if (value !== form.password) return callback(new Error('两次密码不一致'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 密码变化时同步校验确认密码 */
watch(
  () => form.password,
  () => {
    if (form.confirmPassword) {
      formRef.value?.validateField('confirmPassword').catch(() => {})
    }
  },
)

/** 提交注册 */
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    await authStore.register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    ElMessage.success('注册成功，已自动登录')
    router.replace('/')
  } catch {
    // 错误提示已由 utils/request.ts 响应拦截器统一弹窗，此处不再重复
  }
}

/** 跳转登录 */
function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="never">
      <div class="logo-block">
        <el-avatar :size="56" class="logo-avatar">
          <el-icon :size="28"><User /></el-icon>
        </el-avatar>
        <h1 class="logo-title">创建账号</h1>
        <p class="logo-subtitle">加入 AI 开放平台，开启智能之旅</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3-32 位字母、数字或下划线"
            :prefix-icon="User"
            clearable
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱"
            :prefix-icon="Message"
            clearable
            autocomplete="email"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少 8 位，含字母和数字"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
          <!-- 密码强度实时提示 -->
          <div v-if="form.password" class="strength-wrap">
            <el-progress
              :percentage="strength.percentage"
              :color="strength.color"
              :show-text="false"
              :stroke-width="4"
            />
            <span class="strength-label" :style="{ color: strength.color }">
              {{ strength.label }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Key"
            show-password
            autocomplete="new-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="authStore.loading"
            class="submit-btn"
            @click="handleSubmit"
          >
            {{ authStore.loading ? '注册中…' : '注册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <span>已有账号？</span>
        <a class="auth-link" @click="goLogin">去登录</a>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 50%, #eff6ff 100%);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  padding: var(--space-8) var(--space-6);
}
.auth-card :deep(.el-card__body) {
  padding: 0;
}

.logo-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-6);
  text-align: center;
}
.logo-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 8px 24px rgb(99 102 241 / 0.35);
}
.logo-title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  margin: 0;
}
.logo-subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin: 0;
}

.auth-card :deep(.el-form-item__label) {
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  padding-bottom: 4px;
}

.strength-wrap {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  margin-top: 6px;
}
.strength-wrap :deep(.el-progress) {
  flex: 1;
}
.strength-label {
  font-size: 12px;
  font-weight: var(--font-weight-medium);
  min-width: 28px;
  text-align: right;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none;
  box-shadow: 0 4px 12px rgb(99 102 241 / 0.3);
}
.submit-btn:hover {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  box-shadow: 0 6px 16px rgb(99 102 241 / 0.4);
}

.auth-footer {
  margin-top: var(--space-5);
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}
.auth-link {
  color: var(--color-primary-600);
  cursor: pointer;
  margin-left: 4px;
  transition: color var(--transition-fast);
}
.auth-link:hover {
  color: var(--color-primary-700);
  text-decoration: underline;
}

@media (max-width: 640px) {
  .auth-page {
    padding: var(--space-4);
    align-items: flex-start;
    padding-top: var(--space-12);
  }
  .auth-card {
    max-width: 100%;
    padding: var(--space-6) var(--space-4);
    box-shadow: var(--shadow-sm);
  }
}
</style>
