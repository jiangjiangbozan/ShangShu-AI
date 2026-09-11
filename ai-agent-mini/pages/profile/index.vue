<script setup lang="ts">
/**
 * 个人中心页（TabBar）
 *
 * 对标 Web 端 ProfileView（精简版）。
 * 展示用户信息、网络搜索开关、注销。
 */
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import NavBar from '@/components/common/NavBar.vue'

const authStore = useAuthStore()
const chatStore = useChatStore()

/** 用户显示名 */
const displayName = computed(() => authStore.user?.username || '未登录')
/** 用户邮箱 */
const displayEmail = computed(() => authStore.user?.email || '')

/** 注销确认 */
function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: async (res) => {
      if (res.confirm) {
        await authStore.logout()
        uni.reLaunch({ url: '/pages/login/index' })
      }
    },
  })
}

/** 跳转更多功能页 */
function goPage(path: string) {
  uni.navigateTo({ url: path })
}
</script>

<template>
  <view class="profile-page">
    <NavBar title="我的" />

    <!-- 用户信息卡片 -->
    <view class="profile-card">
      <view class="profile-card__avatar">
        <text class="profile-card__avatar-text">{{ displayName.charAt(0).toUpperCase() }}</text>
      </view>
      <view class="profile-card__info">
        <text class="profile-card__name">{{ displayName }}</text>
        <text v-if="displayEmail" class="profile-card__email">{{ displayEmail }}</text>
      </view>
    </view>

    <!-- 设置列表 -->
    <view class="settings-section">
      <text class="settings-section__title">对话设置</text>

      <!-- 网络搜索增强 -->
      <view class="settings-item">
        <view class="settings-item__left">
          <text class="settings-item__label">网络搜索增强</text>
          <text class="settings-item__desc">开启后 AI 回答将参考网络搜索结果</text>
        </view>
        <switch
          :checked="chatStore.isWebSearchEnabled"
          color="#6366f1"
          @change="chatStore.toggleWebSearch()"
        />
      </view>
    </view>

    <!-- 更多功能 -->
    <view class="settings-section">
      <text class="settings-section__title">更多功能</text>

      <view class="settings-item" @tap="goPage('/pages/knowledge/index')">
        <view class="settings-item__left">
          <text class="settings-item__label">知识库 RAG</text>
          <text class="settings-item__desc">知识库管理与检索测试</text>
        </view>
        <text class="settings-item__arrow">›</text>
      </view>

      <view class="settings-item" @tap="goPage('/pages/agent/index')">
        <view class="settings-item__left">
          <text class="settings-item__label">Agent 任务编排</text>
          <text class="settings-item__desc">Runtime 生命周期与 Trace 回放</text>
        </view>
        <text class="settings-item__arrow">›</text>
      </view>

      <view class="settings-item" @tap="goPage('/pages/customer-service/index')">
        <view class="settings-item__left">
          <text class="settings-item__label">客服工作台</text>
          <text class="settings-item__desc">会话接管、标注回流与命中统计</text>
        </view>
        <text class="settings-item__arrow">›</text>
      </view>

      <view class="settings-item" @tap="goPage('/pages/data-analysis/index')">
        <view class="settings-item__left">
          <text class="settings-item__label">数据分析</text>
          <text class="settings-item__desc">自然语言问数与图表可视化</text>
        </view>
        <text class="settings-item__arrow">›</text>
      </view>
    </view>

    <!-- 关于 -->
    <view class="settings-section">
      <text class="settings-section__title">关于</text>
      <view class="settings-item">
        <view class="settings-item__left">
          <text class="settings-item__label">版本</text>
        </view>
        <text class="settings-item__value">1.0.0</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <button class="logout-btn" @tap="handleLogout">退出登录</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';
@import '@/styles/mixins.scss';

.profile-page {
  min-height: 100vh;
  background-color: $bg-page;
  padding-bottom: $tabbar-height;
}

/* 用户信息卡片 */
.profile-card {
  display: flex;
  align-items: center;
  gap: $space-4;
  padding: $space-5;
  margin: $space-3;
  background-color: $bg-card;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;
}

.profile-card__avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: $radius-full;
  background: linear-gradient(135deg, $color-primary, $color-primary-400);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile-card__avatar-text {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  color: $text-inverse;
}

.profile-card__info {
  flex: 1;
  min-width: 0;
}

.profile-card__name {
  display: block;
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.profile-card__email {
  display: block;
  font-size: $font-size-sm;
  color: $text-tertiary;
  margin-top: 4rpx;
}

/* 设置分区 */
.settings-section {
  margin: $space-3;
}

.settings-section__title {
  display: block;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-tertiary;
  margin-bottom: $space-2;
  padding-left: $space-1;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-3 $space-4;
  background-color: $bg-card;
  border-radius: $radius-lg;
  margin-bottom: 2rpx;

  &:first-of-type {
    border-radius: $radius-lg $radius-lg $radius-sm $radius-sm;
  }

  &:last-of-type {
    border-radius: $radius-sm $radius-sm $radius-lg $radius-lg;
    margin-bottom: 0;
  }

  &:only-of-type {
    border-radius: $radius-lg;
  }
}

.settings-item__left {
  flex: 1;
  min-width: 0;
}

.settings-item__label {
  display: block;
  font-size: $font-size-base;
  color: $text-primary;
}

.settings-item__desc {
  display: block;
  font-size: $font-size-xs;
  color: $text-tertiary;
  margin-top: 4rpx;
}

.settings-item__value {
  font-size: $font-size-sm;
  color: $text-tertiary;
}

.settings-item__arrow {
  font-size: $font-size-xl;
  color: $text-tertiary;
  margin-left: $space-3;
}

/* 退出登录 */
.logout-section {
  padding: $space-8 $space-4;
}

.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background-color: $bg-card;
  color: $color-danger;
  font-size: $font-size-base;
  font-weight: $font-weight-medium;
  border-radius: $radius-lg;
  border: 2rpx solid $color-danger-soft;

  &:active {
    background-color: $color-danger-soft;
  }
}
</style>
