<script setup lang="ts">
/**
 * 自定义导航栏
 *
 * 适配微信小程序沉浸式状态栏，支持：
 * - 自定义标题
 * - 返回按钮（非 TabBar 页面使用）
 * - 透明模式（聊天页使用）
 */
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 标题 */
    title?: string
    /** 是否显示返回按钮 */
    showBack?: boolean
    /** 是否透明模式 */
    transparent?: boolean
    /** 背景色（transparent=false 时使用） */
    bgColor?: string
  }>(),
  {
    title: '',
    showBack: false,
    transparent: false,
    bgColor: '#ffffff',
  },
)

const emit = defineEmits<{
  back: []
}>()

/** 状态栏高度（px） */
const statusBarHeight = ref(0)
/** 导航栏内容高度（px） */
const navBarContentHeight = 44

try {
  const systemInfo = uni.getSystemInfoSync()
  statusBarHeight.value = systemInfo.statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 44
}

/** 导航栏总高度 = 状态栏 + 内容区 */
const totalHeight = statusBarHeight.value + navBarContentHeight

function handleBack() {
  emit('back')
  uni.navigateBack({ delta: 1 })
}
</script>

<template>
  <view
    class="nav-bar"
    :class="{ 'nav-bar--transparent': transparent }"
    :style="{
      backgroundColor: transparent ? 'transparent' : bgColor,
    }"
  >
    <!-- 状态栏占位 -->
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 导航栏内容 -->
    <view class="nav-bar__content" :style="{ height: navBarContentHeight + 'px' }">
      <!-- 返回按钮 -->
      <view v-if="showBack" class="nav-bar__back" @tap="handleBack">
        <view class="nav-bar__back-icon" />
        <text class="nav-bar__back-text">返回</text>
      </view>

      <!-- 标题 -->
      <view class="nav-bar__title">
        <text>{{ title }}</text>
      </view>

      <!-- 右侧插槽 -->
      <view class="nav-bar__right">
        <slot name="right" />
      </view>
    </view>
  </view>

  <!-- 占位（非透明模式下撑开页面内容） -->
  <view v-if="!transparent" :style="{ height: totalHeight + 'px' }" />
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;

  &--transparent {
    .nav-bar__title text {
      color: $text-primary;
    }
  }
}

.nav-bar__content {
  display: flex;
  align-items: center;
  padding: 0 $space-3;
  position: relative;
}

.nav-bar__back {
  display: flex;
  align-items: center;
  padding: $space-1 $space-2;
  margin-right: $space-2;

  &:active {
    opacity: 0.7;
  }
}

.nav-bar__back-icon {
  // CSS 绘制左箭头（不依赖 iconfont 字体，避免图标缺失）
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid $text-primary;
  border-bottom: 4rpx solid $text-primary;
  transform: rotate(45deg);
  margin-right: 10rpx;
}

.nav-bar__back-text {
  font-size: $font-size-base;
  color: $text-primary;
}

.nav-bar__title {
  flex: 1;
  text-align: center;
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-bar__right {
  min-width: 80rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>
