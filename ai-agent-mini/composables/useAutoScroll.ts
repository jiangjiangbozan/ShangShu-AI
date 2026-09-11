/**
 * 消息列表自动滚底组合式函数
 *
 * 对标 Web 端 MessageList 的自动滚动逻辑。
 * 小程序使用 scroll-view 的 scroll-top 属性控制滚动。
 *
 * 流式滚底策略：
 * - 新消息加入时立即滚底
 * - 流式输出期间节流滚底（300ms），避免每个 chunk 都触发滚动
 * - 流式结束后强制滚底一次
 */
import { ref, watch, nextTick } from 'vue'

/** 流式滚底节流间隔（毫秒） */
const SCROLL_THROTTLE_MS = 300

/**
 * @param messages 消息列表引用（用于监听变化）
 * @param isStreaming 是否正在流式输出
 */
export function useAutoScroll(
  messages: { value: unknown[] },
  isStreaming: { value: boolean },
) {
  /** scroll-view 的 scroll-top 值 */
  const scrollTop = ref(0)
  /** 是否启用自动滚底（用户手动上滑时暂停） */
  const autoScrollEnabled = ref(true)
  /** scroll-view 实例引用 */
  const scrollViewRef = ref<unknown>(null)
  /** 流式滚底节流定时器 */
  let scrollTimer: ReturnType<typeof setTimeout> | null = null
  /** 上次滚底时间 */
  let lastScrollTime = 0

  /** 滚到底部 */
  function scrollToBottom() {
    if (!autoScrollEnabled.value) return
    nextTick(() => {
      // 通过切换 scrollTop 值触发滚动
      scrollTop.value = scrollTop.value === 99999 ? 100000 : 99999
    })
  }

  /**
   * 流式输出期间的节流滚底
   *
   * 每个 SSE chunk 都会触发 content 变化，如果每次都调用 scrollToBottom
   * 会导致 scroll-view 频繁重排。通过节流控制每 300ms 最多滚一次。
   */
  function throttledScrollToBottom() {
    if (!autoScrollEnabled.value) return
    const now = Date.now()
    if (now - lastScrollTime >= SCROLL_THROTTLE_MS) {
      scrollToBottom()
      lastScrollTime = now
    } else if (!scrollTimer) {
      scrollTimer = setTimeout(() => {
        scrollTimer = null
        scrollToBottom()
        lastScrollTime = Date.now()
      }, SCROLL_THROTTLE_MS - (now - lastScrollTime))
    }
  }

  /** 用户点击“回到底部”按钮时恢复自动滚底 */
  function resumeAutoScroll() {
    autoScrollEnabled.value = true
    scrollToBottom()
  }

  // 监听消息列表变化（新消息加入时）
  watch(
    () => (messages.value ?? []).length,
    () => {
      if (autoScrollEnabled.value) scrollToBottom()
    },
  )

  // 监听流式状态：开始时恢复自动滚底，结束时强制滚底 + 清理定时器
  watch(
    () => isStreaming.value,
    (streaming) => {
      if (streaming) {
        autoScrollEnabled.value = true
        lastScrollTime = 0
      } else {
        // 流式结束：清理节流定时器，强制滚底一次
        if (scrollTimer) {
          clearTimeout(scrollTimer)
          scrollTimer = null
        }
        scrollToBottom()
      }
    },
  )

  // 监听最后一条消息的内容变化（流式输出期间持续触发）
  watch(
    () => {
      const msgs = messages.value
      if (!msgs || msgs.length === 0) return ''
      return (msgs[msgs.length - 1] as { content?: string }).content ?? ''
    },
    () => {
      if (isStreaming.value) {
        throttledScrollToBottom()
      }
    },
  )

  return {
    scrollTop,
    autoScrollEnabled,
    scrollViewRef,
    scrollToBottom,
    throttledScrollToBottom,
    resumeAutoScroll,
  }
}
