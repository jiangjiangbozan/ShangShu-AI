<script setup lang="ts">
/**
 * 忘记密码页
 *
 * 单字段 email，校验合法邮箱
 * 点击"发送重置链接"，成功后 ElMessage.success 并禁用按钮 60 秒倒计时
 * 底部链接：想起密码了？去登录
 */
import { reactive, ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElAvatar,
  ElIcon,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import { forgotPassword } from '@/api/auth'

const router = useRouter()

const formRef = ref<FormInstance>()
const form = reactive({ email: '' })

/** 发送中（请求进行中） */
const sending = ref(false)
/** 倒计时剩余秒数 */
const countdown = ref(0)
/** 定时器句柄 */
let timer: ReturnType<typeof setInterval> | null = null

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

/** 启动 60s 倒计时 */
function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      stopTimer()
    }
  }, 1000)
}

/** 停止定时器 */
function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  countdown.value = 0
}

onUnmounted(stopTimer)

/** 提交发送重置链接 */
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  sending.value = true
  try {
    await forgotPassword(form.email.trim())
    ElMessage.success('重置链接已发送至邮箱，请查收')
    startCountdown()
  } catch {
    // 错误提示已由 utils/request.ts 响应拦截器统一弹窗，此处不再重复
  } finally {
    sending.value = false
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
          <el-icon :size="28"><Message /></el-icon>
        </el-avatar>
        <h1 class="logo-title">忘记密码</h1>
        <p class="logo-subtitle">输入注册邮箱，我们将向您发送重置链接</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入注册邮箱"
            :prefix-icon="Message"
            clearable
            autocomplete="email"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="sending"
            :disabled="countdown > 0"
            class="submit-btn"
            @click="handleSubmit"
          >
            <span v-if="countdown > 0">{{ countdown }} 秒后可重新发送</span>
            <span v-else>{{ sending ? '发送中…' : '发送重置链接' }}</span>
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <span>想起密码了？</span>
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
  max-width: 400px;
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
.submit-btn:disabled {
  opacity: 0.7;
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
