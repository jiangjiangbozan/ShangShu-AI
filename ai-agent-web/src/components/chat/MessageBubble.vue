<script setup lang="ts">
/**
 * 消息气泡组件
 *
 * 支持 Markdown 渲染、代码块高亮、复制按钮。
 * 流式输出期间 AI 消息为空时显示"思考中"状态指示器。
 *
 * 渲染管线：marked（GFM + breaks 同步解析） → highlight.js 代码高亮 → DOMPurify 净化（防 XSS）。
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElIcon, ElMessage } from 'element-plus'
import { CopyDocument, Loading, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { marked, type Tokens } from 'marked'
import DOMPurify, { type Config as PurifyConfig } from 'dompurify'
import hljs from 'highlight.js/lib/core'
// 按需注册常用语言（避免引入全部 ~600KB）
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/atom-one-dark.css'
import { useChatStore } from '@/stores/chat'
import type { MessageResp } from '@/api/chat'

const props = defineProps<{
  message: MessageResp
}>()

const chatStore = useChatStore()

// 注册 highlight.js 语言
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('markdown', markdown)

/** 是否为用户消息 */
const isUser = computed(() => props.message.role === 'user')

/** 网络搜索引用来源（webSearch 增强时非空），转成 {index, url, host} 列表 */
const sources = computed(() =>
  (props.message.sources ?? []).map((url: string, i: number) => ({
    index: i + 1,
    url,
    host: safeHost(url),
  })),
)

/** 提取 URL 的主机名用于紧凑展示（非法 URL 回退原文） */
function safeHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

/** 知识库 RAG 引用来源（启用 RAG 时非空） */
const references = computed(() => props.message.references ?? [])

/** 引用条目展开状态（按索引记录，避免 computed 重建丢失状态） */
const expandedRefs = reactive(new Set<number>())

/** 切换引用条目展开/收起 */
function toggleReference(index: number) {
  if (expandedRefs.has(index)) {
    expandedRefs.delete(index)
  } else {
    expandedRefs.add(index)
  }
}

/** 引用相似度百分比（0~1 → 0~100%） */
function scorePercent(score: number): string {
  if (score == null || Number.isNaN(score)) return '-'
  return `${Math.round(score * 100)}%`
}

/** 是否处于"思考中"状态：流式输出中且本条 AI 消息内容为空 */
const isThinking = computed(
  () =>
    !isUser.value &&
    chatStore.isStreaming &&
    props.message.content === '' &&
    chatStore.messages[chatStore.messages.length - 1] === props.message,
)

/** 是否处于"正在生成"状态：流式输出中且本条 AI 消息已有部分内容 */
const isGenerating = computed(
  () =>
    !isUser.value &&
    chatStore.isStreaming &&
    props.message.content !== '' &&
    chatStore.messages[chatStore.messages.length - 1] === props.message,
)

// marked v18 renderer.code 签名：({ text, lang, escaped, ... }: Tokens.Code) => string
// text 是代码原文，lang 是语言标识（可能为空），escaped 用于 marked 内部转义标记
// originalCode 兜底需要完整 Code token（含 type/raw 字段），因此保留 token 引用
const renderer = new marked.Renderer()
const originalCode = renderer.code.bind(renderer)
renderer.code = (token: Tokens.Code): string => {
  const { text, lang } = token
  const language = (lang || '').trim().toLowerCase()
  try {
    if (language && hljs.getLanguage(language)) {
      const highlighted = hljs.highlight(text, { language, ignoreIllegals: true }).value
      return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`
    }
  } catch {
    // fallthrough to auto highlight
  }
  try {
    const highlighted = hljs.highlightAuto(text).value
    return `<pre><code class="hljs">${highlighted}</code></pre>`
  } catch {
    // 兜底：调用 marked 原始 renderer.code（接收完整 Code token）
    return originalCode(token)
  }
}
marked.setOptions({
  renderer,
  gfm: true,
  breaks: true,
  async: false,
})

/** 净化配置：允许常见 Markdown 输出标签，禁用危险属性 */
const purifyConfig: PurifyConfig = {
  ALLOWED_TAGS: [
    'p', 'br', 'hr', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'blockquote', 'ul', 'ol', 'li', 'pre', 'code', 'em', 'strong', 'del', 's',
    'a', 'img', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'span', 'div', 'sup', 'sub',
    'input',
  ],
  ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'target', 'rel', 'type', 'checked', 'disabled'],
  ALLOW_DATA_ATTR: false,
  FORBID_ATTR: ['style', 'onerror', 'onload', 'onclick', 'onmouseover'],
}

/** 渲染后的安全 HTML */
const renderedContent = computed(() => {
  if (props.message.role === 'user') {
    return escapeHtml(props.message.content)
  }
  try {
    // marked v18 TS 签名返回 string | Promise<string>，async:false 已强制同步运行，
    // 但类型仍是联合类型，DOMPurify.sanitize 接收 Promise 会被 toString 成 [object Promise]。
    // 用 as unknown as string 断言到 string，避免 v-html 绑定 Promise 对象。
    const raw = marked.parse(props.message.content, { async: false }) as unknown as string
    return DOMPurify.sanitize(raw, purifyConfig)
  } catch (e) {
    console.error('[MessageBubble][marked parse failed]', e)
    return escapeHtml(props.message.content)
  }
})

/** 简单 HTML 转义（用户消息） */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

/** 复制消息内容 */
function copyContent() {
  navigator.clipboard.writeText(props.message.content).then(
    () => ElMessage.success('已复制到剪贴板'),
    () => ElMessage.error('复制失败'),
  )
}

/** 链接点击拦截：外部链接新窗口打开，避免 SPA 跳转 */
const bubbleRef = ref<HTMLDivElement>()
watch(
  () => renderedContent.value,
  () => {
    nextTick(() => {
      const root = bubbleRef.value
      if (!root) return
      root.querySelectorAll('a[href]').forEach((el) => {
        const a = el as HTMLAnchorElement
        if (/^https?:\/\//.test(a.href)) {
          a.target = '_blank'
          a.rel = 'noopener noreferrer'
        }
      })
    })
  },
  { immediate: true },
)
</script>

<template>
  <div class="message-bubble" :class="{ 'is-user': isUser, 'is-assistant': !isUser }" :data-message-id="message.messageId">
    <div class="bubble-avatar">
      <el-icon :size="22">
        <Loading v-if="isThinking" />
        <span v-else-if="isUser" class="avatar-text">我</span>
        <span v-else class="avatar-text">AI</span>
      </el-icon>
    </div>
    <div class="bubble-body">
      <div class="bubble-header">
        <span class="bubble-role">{{ isUser ? '我' : 'AI 助手' }}</span>
        <span v-if="isThinking" class="bubble-status">思考中…</span>
        <span v-else-if="isGenerating" class="bubble-status">生成中…</span>
      </div>
      <div ref="bubbleRef" class="bubble-content" v-html="renderedContent" />
      <!-- 网络搜索引用来源（webSearch 增强时展示） -->
      <div v-if="sources.length" class="bubble-sources">
        <span class="sources-title">🌐 引用来源</span>
        <a
          v-for="s in sources"
          :key="s.url"
          :href="s.url"
          target="_blank"
          rel="noopener noreferrer"
          class="source-link"
          :title="s.url"
        >
          [{{ s.index }}] {{ s.host }}
        </a>
      </div>
      <!-- 知识库 RAG 引用来源标注（启用 RAG 时展示，点击展开分片内容） -->
      <div v-if="references.length" class="bubble-references">
        <span class="sources-title">📚 知识库引用</span>
        <div
          v-for="(ref, i) in references"
          :key="ref.docId + '-' + i"
          class="reference-item"
          @click="toggleReference(i)"
        >
          <div class="reference-head">
            <span class="reference-index">[{{ i + 1 }}]</span>
            <span class="reference-docid" :title="ref.docId">doc: {{ ref.docId }}</span>
            <el-tag size="small" type="warning" class="reference-score">
              相似度 {{ scorePercent(ref.score) }}
            </el-tag>
            <el-icon class="reference-arrow" :class="{ expanded: expandedRefs.has(i) }">
              <component :is="expandedRefs.has(i) ? ArrowUp : ArrowDown" />
            </el-icon>
          </div>
          <div v-if="expandedRefs.has(i)" class="reference-content">{{ ref.content }}</div>
        </div>
      </div>
      <div class="bubble-footer">
        <el-button text size="small" @click="copyContent">
          <el-icon><CopyDocument /></el-icon>
          <span>复制</span>
        </el-button>
        <span v-if="props.message.latencyMs" class="bubble-latency">
          {{ props.message.latencyMs }}ms
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-bubble {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
}
.bubble-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-bg, #e8f0ff);
  color: var(--color-primary);
  flex-shrink: 0;
}
.avatar-text {
  font-size: 12px;
  font-weight: 600;
}
.bubble-body {
  flex: 1;
  min-width: 0;
}
.bubble-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 4px;
}
.bubble-role {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.bubble-status {
  font-size: 12px;
  color: var(--text-tertiary);
  font-style: italic;
}
.bubble-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
}

/* 用户消息样式：右对齐气泡 */
.is-user .bubble-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.is-user .bubble-content {
  background: var(--color-primary);
  color: white;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  border-bottom-right-radius: var(--radius-sm);
}

/* AI 消息样式：左对齐气泡 */
.is-assistant .bubble-content {
  background: var(--bg-card, #fff);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-default);
  border-bottom-left-radius: var(--radius-sm);
}

/* Markdown 渲染样式 */
.bubble-content :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--space-2) 0;
}
.bubble-content :deep(code) {
  font-family: 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
}
.bubble-content :deep(p) {
  margin: 0 0 var(--space-2);
}
.bubble-content :deep(p:last-child) {
  margin-bottom: 0;
}
.bubble-content :deep(ul),
.bubble-content :deep(ol) {
  padding-left: var(--space-5);
  margin: var(--space-2) 0;
}
.bubble-content :deep(blockquote) {
  border-left: 3px solid var(--color-primary);
  padding-left: var(--space-3);
  margin: var(--space-2) 0;
  color: var(--text-secondary);
}

.bubble-footer {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-1);
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.message-bubble:hover .bubble-footer {
  opacity: 1;
}

.bubble-latency {
  font-size: 11px;
  color: var(--text-tertiary);
}

/* 网络搜索引用来源 */
.bubble-sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--bg-page, #f5f7fa);
  border-radius: var(--radius-sm);
  font-size: 12px;
}
.sources-title {
  color: var(--text-secondary);
  font-weight: 600;
}
.source-link {
  color: var(--color-primary);
  text-decoration: none;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-link:hover {
  text-decoration: underline;
}

/* 知识库 RAG 引用来源标注 */
.bubble-references {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--bg-page, #f5f7fa);
  border-radius: var(--radius-sm);
  font-size: 12px;
}
.reference-item {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
  cursor: pointer;
  transition: background 0.2s;
}
.reference-item:hover {
  background: rgba(64, 158, 255, 0.06);
}
.reference-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.reference-index {
  color: var(--text-secondary);
  font-weight: 600;
}
.reference-docid {
  color: var(--color-primary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reference-score {
  margin-left: auto;
}
.reference-arrow {
  color: var(--text-tertiary);
  transition: transform 0.2s;
}
.reference-arrow.expanded {
  transform: rotate(180deg);
}
.reference-content {
  margin-top: var(--space-2);
  padding: var(--space-2);
  background: var(--bg-page, #f5f7fa);
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  line-height: 1.6;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
