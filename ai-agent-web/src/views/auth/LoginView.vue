 <script setup lang="ts">
/**
 * 登录页
 *
 * 设计理念：
 * - 居中卡片布局（max-width 400px），sm 屏幕全宽，md+ 居中
 * - 顶部 Logo 区（Element Plus Avatar 占位）
 * - 账号 / 密码 双字段，Element Plus 表单校验
 * - 登录成功后回跳 route.query.redirect 或首页
 * - 底部链接：去注册 / 忘记密码
 */
import { reactive, ref } from 'vue'
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
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getOAuthAuthorizeUrl } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

/** 表单引用 */
const formRef = ref<FormInstance>()

/** 第三方登录跳转中标记 */
const oauthLoading = ref(false)

/** 表单数据 */
const form = reactive({
  account: '',
  password: '',
})

/** 校验规则 */
const rules: FormRules = {
  account: [{ required: true, message: '请输入邮箱或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 提交登录 */
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    await authStore.login({
      account: form.account.trim(),
      password: form.password,
    })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    const target = typeof redirect === 'string' && redirect ? redirect : '/'
    router.replace(target)
  } catch {
    // 错误提示已由 utils/request.ts 响应拦截器统一弹窗，此处不再重复 ElMessage.error
    // 否则会与拦截器叠加产生 2~3 条错误消息（登录失败 + getCurrentUser 二次失败）
  }
}

/** 跳转注册 */
function goRegister() {
  router.push('/register')
}

/** 跳转忘记密码 */
function goForgotPassword() {
  router.push('/forgot-password')
}

/** Gitee 第三方登录：获取授权地址后整页跳转 Gitee 授权页 */
async function handleGiteeLogin() {
  oauthLoading.value = true
  try {
    const resp = await getOAuthAuthorizeUrl('gitee')
    window.location.href = resp.authorizeUrl
  } catch {
    // 错误提示已由请求拦截器统一弹窗
  } finally {
    oauthLoading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="never">
      <!-- ============ Logo 区 ============ -->
      <div class="logo-block">
        <el-avatar :size="56" class="logo-avatar">
          <el-icon :size="28"><User /></el-icon>
        </el-avatar>
        <h1 class="logo-title">AI 开放平台</h1>
        <p class="logo-subtitle">欢迎回来，请登录您的账号</p>
      </div>

      <!-- ============ 表单 ============ -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="账号" prop="account">
          <el-input
            v-model="form.account"
            placeholder="邮箱或用户名"
            :prefix-icon="User"
            clearable
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
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
            {{ authStore.loading ? '登录中…' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- ============ 第三方登录 ============ -->
      <div class="oauth-block">
        <div class="oauth-divider">
          <span class="divider-line"></span>
          <span class="divider-text">第三方登录</span>
          <span class="divider-line"></span>
        </div>
        <el-button
          class="gitee-btn"
          :loading="oauthLoading"
          @click="handleGiteeLogin"
        >
          使用 Gitee 登录
        </el-button>
      </div>

      <!-- ============ 底部链接 ============ -->
      <div class="auth-footer">
        <span>还没有账号？</span>
        <a class="auth-link" @click="goRegister">立即注册！</a>
        <span class="footer-divider">·</span>
        <a class="auth-link" @click="goForgotPassword">忘记密码？</a>
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

/* ============ Logo 区 ============ */
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

/* ============ 表单 ============ */

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

/* ============ 底部链接 ============ */
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

.footer-divider {
  margin: 0 8px;
  color: var(--text-tertiary);
}

/* ============ 第三方登录 ============ */
.oauth-block {
  margin-top: var(--space-5);
}

.oauth-divider {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.divider-line {
  flex: 1;
  height: 1px;
  background: var(--border-default);
}

.divider-text {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  white-space: nowrap;
}

.gitee-btn {
  width: 100%;
  background: #fff;
  border: 1px solid var(--border-default);
  color: var(--text-primary);
}
.gitee-btn:hover {
  border-color: #c71d23;
  color: #c71d23;
}

/* ============ 响应式 ============ */
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
