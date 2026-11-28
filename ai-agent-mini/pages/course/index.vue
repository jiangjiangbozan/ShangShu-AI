<script setup lang="ts">
/**
 * 计算机网络课程智能问答页
 *
 * 对接后端课程问答接口（/course/question）：
 * 后端做意图分类（知识点/习题/闲聊）→ 四路召回（含知识图谱多跳）→ 课程 prompt → 模型回答。
 * 采用 JSON 同步返回（非流式），页面自管理消息列表。
 */
import { ref, nextTick } from 'vue'
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { askCourseQuestion } from '@/api/course'
import NavBar from '@/components/common/NavBar.vue'

/** 对话消息 */
interface CourseMessage {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<CourseMessage[]>([])
const inputText = ref('')
const sending = ref(false)
/** 滚动锚点 */
const scrollTop = ref(0)

onShareAppMessage(() => ({
  title: '计算机网络课程 AI 助手',
  path: '/pages/course/index',
}))

onShareTimeline(() => ({
  title: '计算机网络课程 AI 助手',
}))

/** 发送问题 */
async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  inputText.value = ''
  messages.value.push({ role: 'user', content: text })
  sending.value = true
  scrollToBottom()

  // 历史对话（不含刚 push 的用户消息）
  const history = messages.value
    .slice(0, -1)
    .map((m) => ({ role: m.role, content: m.content }))

  try {
    const answer = await askCourseQuestion({ content: text, history })
    messages.value.push({ role: 'assistant', content: answer || '（无回答）' })
  } catch (err: unknown) {
    messages.value.push({
      role: 'assistant',
      content: `[错误] ${err instanceof Error ? err.message : '回答失败，请重试'}`,
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

/** 滚动到底部（text 列表用 scroll-into-view 简易实现：重置 scrollTop 触发刷新） */
function scrollToBottom() {
  nextTick(() => {
    // 用极大值使 scroll-view 滚到底部
    scrollTop.value = 999999
  })
}
</script>

<template>
  <view class="course-page">
    <NavBar title="课程问答" :show-back="true" />

    <scroll-view scroll-y class="course-page__list" :scroll-top="scrollTop">
      <!-- 空状态 -->
      <view v-if="messages.length === 0" class="course-page__empty">
        <text class="course-page__empty-title">计算机网络课程助手</text>
        <text class="course-page__empty-tip">
          可以问我知识点（如"什么是 TCP 三次握手"）或习题（如"这道题怎么做"）
        </text>
      </view>

      <!-- 消息列表 -->
      <view v-else class="course-page__messages">
        <view
          v-for="(msg, idx) in messages"
          :key="idx"
          class="course-msg"
          :class="`course-msg--${msg.role}`"
        >
          <text class="course-msg__content">{{ msg.content }}</text>
        </view>
        <view v-if="sending" class="course-msg course-msg--assistant">
          <text class="course-msg__content">思考中...</text>
        </view>
      </view>
    </scroll-view>

    <!-- 输入区 -->
    <view class="course-page__input-bar">
      <input
        v-model="inputText"
        class="course-page__input"
        placeholder="输入课程问题..."
        confirm-type="send"
        :disabled="sending"
        @confirm="handleSend"
      />
      <button
        class="course-page__send"
        :disabled="sending || !inputText.trim()"
        @tap="handleSend"
      >
        发送
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.course-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-page;
}

.course-page__list {
  flex: 1;
  padding: $space-4;
}

.course-page__empty {
  padding-top: 160rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.course-page__empty-title {
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-primary;
  margin-bottom: $space-3;
}

.course-page__empty-tip {
  font-size: $font-size-sm;
  color: $text-tertiary;
  text-align: center;
  padding: 0 $space-8;
  line-height: $line-height-relaxed;
}

.course-page__messages {
  padding-bottom: $space-4;
}

.course-msg {
  display: flex;
  margin-bottom: $space-3;

  &--user {
    justify-content: flex-end;
  }

  &--assistant {
    justify-content: flex-start;
  }
}

.course-msg__content {
  max-width: 80%;
  padding: $space-2 $space-3;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  line-height: $line-height-relaxed;
  word-break: break-word;
  white-space: pre-wrap;
}

.course-msg--user .course-msg__content {
  background-color: $color-primary;
  color: $text-inverse;
}

.course-msg--assistant .course-msg__content {
  background-color: $bg-card;
  color: $text-primary;
}

.course-page__input-bar {
  display: flex;
  align-items: center;
  gap: $space-2;
  padding: $space-2 $space-3;
  background-color: $bg-card;
  border-top: 2rpx solid $border-subtle;
}

.course-page__input {
  flex: 1;
  height: 80rpx;
  padding: 0 $space-3;
  border: 2rpx solid $border-default;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  background-color: $bg-page;
}

.course-page__send {
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 $space-4;
  background-color: $color-primary;
  color: $text-inverse;
  border-radius: $radius-lg;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  text-align: center;

  &[disabled] {
    opacity: 0.5;
  }
}
</style>
