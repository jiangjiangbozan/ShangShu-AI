<script setup lang="ts">
/**
 * 会话卡片组件
 *
 * 对标 Web 端 SessionList 中的列表项。
 * 支持长按删除确认。
 */
import type { SessionResp } from '@/types/chat'

const props = defineProps<{
  session: SessionResp
  isActive: boolean
}>()

const emit = defineEmits<{
  select: [sessionId: number]
  delete: [sessionId: number]
}>()

/** 格式化时间 */
function formatTime(dateStr: string): string {
  try {
    const date = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    const diffHour = Math.floor(diffMs / 3600000)
    const diffDay = Math.floor(diffMs / 86400000)

    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin}分钟前`
    if (diffHour < 24) return `${diffHour}小时前`
    if (diffDay < 7) return `${diffDay}天前`
    return `${date.getMonth() + 1}/${date.getDate()}`
  } catch {
    return ''
  }
}

function handleSelect() {
  emit('select', props.session.sessionId)
}

function handleDelete() {
  uni.showModal({
    title: '删除会话',
    content: `确定删除「${props.session.title || '未命名会话'}」？`,
    success: (res) => {
      if (res.confirm) {
        emit('delete', props.session.sessionId)
      }
    },
  })
}
</script>

<template>
  <view
    class="session-card"
    :class="{ 'session-card--active': isActive }"
    @tap="handleSelect"
    @longpress="handleDelete"
  >
    <view class="session-card__info">
      <text class="session-card__title">{{ session.title || '未命名会话' }}</text>
      <view class="session-card__meta">
        <text class="session-card__time">{{ formatTime(session.updateTime) }}</text>
        <text v-if="session.totalTokens" class="session-card__tokens">
          {{ session.totalTokens }} tokens
        </text>
      </view>
    </view>
    <view v-if="session.messageCount" class="session-card__badge">
      <text class="session-card__badge-text">{{ session.messageCount }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';
@import '@/styles/mixins.scss';

.session-card {
  display: flex;
  align-items: center;
  padding: $space-3 $space-4;
  margin: 0 $space-3;
  border-radius: $radius-lg;
  background-color: $bg-card;
  margin-bottom: $space-2;
  transition: background-color $transition-fast;

  &:active {
    background-color: $bg-hover;
  }

  &--active {
    background-color: $color-primary-50;
    border-left: 6rpx solid $color-primary;
  }
}

.session-card__info {
  flex: 1;
  min-width: 0;
}

.session-card__title {
  display: block;
  font-size: $font-size-base;
  font-weight: $font-weight-medium;
  color: $text-primary;
  @include text-ellipsis;
}

.session-card__meta {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-top: 4rpx;
}

.session-card__time {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.session-card__tokens {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.session-card__badge {
  min-width: 40rpx;
  height: 40rpx;
  border-radius: $radius-full;
  background-color: $color-primary-100;
  @include flex-center;
  margin-left: $space-2;
}

.session-card__badge-text {
  font-size: 20rpx;
  color: $color-primary;
  font-weight: $font-weight-semibold;
}
</style>
