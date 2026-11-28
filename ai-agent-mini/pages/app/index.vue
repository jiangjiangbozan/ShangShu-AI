<script setup lang="ts">
/**
 * 已发布应用入口页
 *
 * - 展示当前身份可见的公开应用
 * - 支持扫描二维码或分享 scene 直接进入指定应用
 * - 不把发布密钥暴露给客户端，只使用 publicCode
 */
import { ref } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/common/NavBar.vue'
import { listPublicApps, parsePublicCode } from '@/api/publicApp'
import type { PublicApp } from '@/types/publicApp'

const apps = ref<PublicApp[]>([])
const loading = ref(false)

onShow(() => {
  loadApps()
})

onShareAppMessage(() => ({
  title: 'ShangShu AI 应用入口',
  path: '/pages/app/index',
}))

/** 加载可见应用 */
async function loadApps() {
  loading.value = true
  try {
    apps.value = await listPublicApps()
  } catch {
    apps.value = []
  } finally {
    loading.value = false
  }
}

/** 扫描二维码进入应用 */
function scanApp() {
  uni.scanCode({
    success: (result) => {
      const publicCode = parsePublicCode(result.result)
      if (!publicCode) {
        uni.showToast({ title: '二维码内容无效', icon: 'none' })
        return
      }
      openApp(publicCode)
    },
  })
}

/** 跳转到应用聊天页 */
function openApp(publicCode: string) {
  uni.navigateTo({ url: `/pages/app/chat?publicCode=${encodeURIComponent(publicCode)}` })
}
</script>

<template>
  <view class="app-list">
    <NavBar title="应用" />

    <view class="app-list__actions">
      <button class="app-list__scan" @tap="scanApp">扫码打开应用</button>
    </view>

    <view v-if="loading" class="app-list__hint">加载中...</view>
    <view v-else-if="!apps.length" class="app-list__empty">
      <text class="app-list__empty-title">暂无可用应用</text>
      <text class="app-list__empty-tip">可在 Web 端发布应用后扫码使用</text>
    </view>
    <view v-else class="app-list__items">
      <view
        v-for="app in apps"
        :key="app.publicCode"
        class="app-card"
        @tap="openApp(app.publicCode)"
      >
        <view class="app-card__head">
          <text class="app-card__name">{{ app.name }}</text>
          <text class="app-card__type">{{ app.appType }}</text>
        </view>
        <text class="app-card__desc">{{ app.description || '暂无描述' }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.app-list {
  min-height: 100vh;
  background-color: $bg-page;
  padding-bottom: $space-8;
}

.app-list__actions {
  padding: $space-3;
}

.app-list__scan {
  margin: 0;
  height: 76rpx;
  line-height: 76rpx;
  font-size: $font-size-base;
  color: $text-inverse;
  background-color: $color-primary;
  border: none;
  border-radius: $radius-lg;

  &::after {
    border: none;
  }
}

.app-list__hint,
.app-list__empty {
  padding: $space-6 $space-4;
  text-align: center;
  color: $text-tertiary;
  font-size: $font-size-sm;
}

.app-list__empty-title {
  display: block;
  color: $text-primary;
  font-size: $font-size-base;
  font-weight: $font-weight-semibold;
}

.app-list__empty-tip {
  display: block;
  margin-top: $space-2;
}

.app-list__items {
  padding: 0 $space-3;
}

.app-card {
  padding: $space-4;
  margin-bottom: $space-3;
  background-color: $bg-card;
  border-radius: $radius-xl;
  box-shadow: $shadow-xs;
}

.app-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $space-2;
}

.app-card__name {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.app-card__type {
  font-size: $font-size-xs;
  color: $color-primary;
  background-color: $bg-subtle;
  padding: 4rpx 12rpx;
  border-radius: $radius-full;
}

.app-card__desc {
  font-size: $font-size-sm;
  color: $text-secondary;
}
</style>
