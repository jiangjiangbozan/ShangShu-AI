<script setup lang="ts">
/**
 * 重置密码页
 *
 * 通过邮件链接进入，token 从 query.token 获取
 * 字段：newPassword / confirmPassword
 * 校验：密码 ≥8 位含字母+数字、确认一致
 * 成功后跳转登录页
 */
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElAvatar,
  ElIcon,
  ElMessage,
  ElResult,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { Lock, Key } from '@element-plus/icons-vue'
import { resetPassword } from '@/api/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const form = reactive({
  token: '',
  newPassword: '',
  confirmPassword: '',
})

/** 提交中 */
const submitting = ref(false)

/** 是否缺少 token（链接非法） */
const missingToken = ref(false)

const rules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error('请输入新密码'))
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
        if (value !== form.newPassword) return callback(new Error('两次密码不一致'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

onMounted(() => {
  const t = route.query.token
  if (typeof t === 'string' && t) {
    form.token = t
  } else {
    missingToken.value = true
  }
})

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    await resetPassword(form.token, form.newPassword)
    ElMessage.success('密码重置成功，请使用新密码登录')
    router.replace('/login')
  } catch (error) {
    const msg = error instanceof Error ? error.message : '重置失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="never">
      <!-- 缺少 token 提示 -->
      <el-result
        v-if="missingToken"
        icon="warning"
        title="重置链接无效"
        sub-title="未检测到有效的重置令牌，请通过忘记密码重新获取链接。"
      >
        <template #extra>
          <el-button type="primary" @click="router.push('/forgot-password')">
            重新获取链接
          </el-button>
        </template>
      </el-result>

      <!-- 正常表单 -->
      <template v-else>
        <div class="logo-block">
          <el-avatar :size="56" class="logo-avatar">
            <el-icon :size="28"><Key /></el-icon>
          </el-avatar>
          <h1 class="logo-title">重置密码</h1>
          <p class="logo-subtitle">为您的账号设置一个新密码</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              placeholder="至少 8 位，含字母和数字"
              :prefix-icon="Lock"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="new-password"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="submitting"
              class="submit-btn"
              @click="handleSubmit"
            >
              {{ submitting ? '提交中…' : '重置密码' }}
            </el-button>
          </el-form-item>
        </el-form>
      </template>
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
