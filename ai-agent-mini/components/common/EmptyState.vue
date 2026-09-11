<script setup lang="ts">
/**
 * 空状态占位组件
 */
defineProps<{
  /** 图标（emoji 或文字） */
  icon?: string
  /** 主标题 */
  title?: string
  /** 副标题 */
  subtitle?: string
}>()
</script>

<template>
  <view class="empty-state">
    <text class="empty-state__icon">{{ icon || '💬' }}</text>
    <text v-if="title" class="empty-state__title">{{ title }}</text>
    <text v-if="subtitle" class="empty-state__subtitle">{{ subtitle }}</text>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $space-12 $space-6;
}

.empty-state__icon {
  font-size: 96rpx;
  margin-bottom: $space-4;
}

.empty-state__title {
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-secondary;
  margin-bottom: $space-2;
}

.empty-state__subtitle {
  font-size: $font-size-base;
  color: $text-tertiary;
  text-align: center;
}
</style>
