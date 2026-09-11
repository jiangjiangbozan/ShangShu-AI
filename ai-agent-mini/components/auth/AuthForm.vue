<script setup lang="ts">
/**
 * 登录/注册表单组件
 *
 * 支持登录与注册模式切换，表单校验，提交。
 * 对标 Web 端 LoginView + RegisterView 的合并精简版。
 */
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  /** 初始模式：login | register */
  mode?: 'login' | 'register'
}>()

const emit = defineEmits<{
  /** 登录/注册成功 */
  success: []
}>()

const authStore = useAuthStore()

/** 当前模式 */
const currentMode = ref<'login' | 'register'>(props.mode ?? 'login')
const isLogin = computed(() => currentMode.value === 'login')
const formTitle = computed(() => (isLogin.value ? '登录' : '注册'))

/** 表单字段 */
const account = ref('')
const password = ref('')
const confirmPassword = ref('')
const username = ref('')
const email = ref('')

/** 错误提示 */
const errorMsg = ref('')

/** 表单校验 */
function validate(): boolean {
  errorMsg.value = ''

  if (isLogin.value) {
    if (!account.value.trim()) {
      errorMsg.value = '请输入账号'
      return false
    }
    if (!password.value) {
      errorMsg.value = '请输入密码'
      return false
    }
  } else {
    if (!username.value.trim()) {
      errorMsg.value = '请输入用户名'
      return false
    }
    if (!email.value.trim()) {
      errorMsg.value = '请输入邮箱'
      return false
    }
    // 简单邮箱格式校验
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) {
      errorMsg.value = '邮箱格式不正确'
      return false
    }
    if (!password.value || password.value.length < 6) {
      errorMsg.value = '密码至少 6 位'
      return false
    }
    if (password.value !== confirmPassword.value) {
      errorMsg.value = '两次密码不一致'
      return false
    }
  }

  return true
}

/** 提交表单 */
async function handleSubmit() {
  if (!validate()) return

  try {
    if (isLogin.value) {
      await authStore.login({ account: account.value, password: password.value })
    } else {
      await authStore.register({
        username: username.value,
        email: email.value,
        password: password.value,
      })
    }
    emit('success')
  } catch (err: unknown) {
    errorMsg.value = err instanceof Error ? err.message : '操作失败，请重试'
  }
}

/** 切换模式 */
function toggleMode() {
  currentMode.value = isLogin.value ? 'register' : 'login'
  errorMsg.value = ''
}
</script>

<template>
  <view class="auth-form">
    <!-- 标题 -->
    <view class="auth-form__header">
      <text class="auth-form__title">{{ formTitle }}</text>
      <text class="auth-form__subtitle">AI 智能助手</text>
    </view>

    <!-- 错误提示 -->
    <view v-if="errorMsg" class="auth-form__error">
      <text>{{ errorMsg }}</text>
    </view>

    <!-- 表单 -->
    <view class="auth-form__fields">
      <!-- 注册：用户名 -->
      <view v-if="!isLogin" class="auth-form__field">
        <text class="auth-form__label">用户名</text>
        <input
          v-model="username"
          class="auth-form__input"
          placeholder="请输入用户名"
          maxlength="32"
        />
      </view>

      <!-- 注册：邮箱 -->
      <view v-if="!isLogin" class="auth-form__field">
        <text class="auth-form__label">邮箱</text>
        <input
          v-model="email"
          class="auth-form__input"
          type="email"
          placeholder="请输入邮箱"
          maxlength="64"
        />
      </view>

      <!-- 登录：账号 -->
      <view v-if="isLogin" class="auth-form__field">
        <text class="auth-form__label">账号</text>
        <input
          v-model="account"
          class="auth-form__input"
          placeholder="邮箱或用户名"
          maxlength="64"
        />
      </view>

      <!-- 密码 -->
      <view class="auth-form__field">
        <text class="auth-form__label">密码</text>
        <input
          v-model="password"
          class="auth-form__input"
          type="password"
          placeholder="请输入密码"
          maxlength="64"
        />
      </view>

      <!-- 注册：确认密码 -->
      <view v-if="!isLogin" class="auth-form__field">
        <text class="auth-form__label">确认密码</text>
        <input
          v-model="confirmPassword"
          class="auth-form__input"
          type="password"
          placeholder="请再次输入密码"
          maxlength="64"
        />
      </view>
    </view>

    <!-- 提交按钮 -->
    <button
      class="auth-form__submit"
      :loading="authStore.loading"
      :disabled="authStore.loading"
      @tap="handleSubmit"
    >
      {{ isLogin ? '登录' : '注册' }}
    </button>

    <!-- 切换模式 -->
    <view class="auth-form__toggle">
      <text class="auth-form__toggle-text" @tap="toggleMode">
        {{ isLogin ? '没有账号？点击注册' : '已有账号？点击登录' }}
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';
@import '@/styles/mixins.scss';

.auth-form {
  padding: $space-8 $space-6;
}

.auth-form__header {
  text-align: center;
  margin-bottom: $space-8;
}

.auth-form__title {
  display: block;
  font-size: $font-size-2xl;
  font-weight: $font-weight-bold;
  color: $text-primary;
  margin-bottom: $space-2;
}

.auth-form__subtitle {
  display: block;
  font-size: $font-size-base;
  color: $text-tertiary;
}

.auth-form__error {
  background-color: $color-danger-soft;
  border-radius: $radius-md;
  padding: $space-2 $space-3;
  margin-bottom: $space-4;

  text {
    font-size: $font-size-sm;
    color: $color-danger;
  }
}

.auth-form__fields {
  margin-bottom: $space-6;
}

.auth-form__field {
  margin-bottom: $space-4;
}

.auth-form__label {
  display: block;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-secondary;
  margin-bottom: $space-1;
}

.auth-form__input {
  width: 100%;
  height: 88rpx;
  padding: 0 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  background-color: $bg-card;
  transition: border-color $transition-fast;

  &:focus {
    border-color: $border-focus;
  }
}

.auth-form__submit {
  @include btn-primary;
  height: 88rpx;
  line-height: 88rpx;
  font-size: $font-size-md;
  margin-bottom: $space-4;
}

.auth-form__toggle {
  text-align: center;
}

.auth-form__toggle-text {
  font-size: $font-size-sm;
  color: $color-primary;

  &:active {
    opacity: 0.7;
  }
}
</style>
