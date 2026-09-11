<script setup lang="ts">
/**
 * 登录/注册页
 *
 * 对标 Web 端 LoginView + RegisterView。
 * 使用自定义导航栏 + AuthForm 组件。
 */
import NavBar from '@/components/common/NavBar.vue'
import AuthForm from '@/components/auth/AuthForm.vue'

/** 登录/注册成功后跳转 */
function handleSuccess() {
  // 跳转到会话列表页（TabBar 首页）
  uni.switchTab({ url: '/pages/chat/index' })
  // 通知会话列表页刷新：
  // TabBar 页已常驻，switchTab 不会重新触发 onMounted，
  // 需通过全局事件触发 fetchSessions，否则历史列表不自动显示。
  uni.$emit('chatIndexShow')
}
</script>

<template>
  <view class="login-page">
    <NavBar title="登录" />

    <scroll-view scroll-y class="login-page__scroll">
      <AuthForm @success="handleSuccess" />
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.login-page {
  min-height: 100vh;
  background-color: $bg-page;
}

.login-page__scroll {
  height: 100vh;
}
</style>
