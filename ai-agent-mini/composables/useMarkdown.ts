/**
 * Markdown 解析组合式函数
 *
 * 将 Markdown 文本解析为小程序 rich-text 组件支持的 HTML 字符串。
 * 块级结构使用 marked 库的 lexer 进行词法分析，自定义 renderer 生成安全 HTML。
 * 行内格式（粗体/斜体/链接等）使用正则直接匹配，避免 lexInline 递归栈溢出。
 * 最后通过 sanitizeHtml 净化防 XSS。
 */
import { computed, type Ref } from 'vue'
import { marked, type Token, type Tokens } from 'marked'
import { sanitizeHtml } from '@/utils/sanitize'

/**
 * 解析 Markdown 为安全 HTML
 * @param content Markdown 源文本
 * @returns 净化后的 HTML 字符串
 */
export function parseMarkdown(content: string): string {
  if (!content) return ''

  try {
    const tokens = marked.lexer(content)
    let html = ''

    for (const token of tokens) {
      html += renderToken(token)
    }

    return sanitizeHtml(html)
  } catch (e) {
    console.error('[useMarkdown] 解析失败:', e)
    return escapeHtml(content)
  }
}

/** 组合式函数：响应式 Markdown 解析 */
export function useMarkdown(content: Ref<string>) {
  const renderedHtml = computed(() => parseMarkdown(content.value))
  return { renderedHtml }
}

/* ============ Token 渲染器 ============ */

function renderToken(token: Token): string {
  switch (token.type) {
    case 'space':
      return ''
    case 'heading':
      return renderHeading(token as Tokens.Heading)
    case 'paragraph':
      return renderParagraph(token as Tokens.Paragraph)
    case 'code':
      return renderCode(token as Tokens.Code)
    case 'blockquote':
      return renderBlockquote(token as Tokens.Blockquote)
    case 'list':
      return renderList(token as Tokens.List)
    case 'hr':
      return '<hr />'
    case 'html':
      return '' // 安全起见，忽略原始 HTML
    case 'table':
      return renderTable(token as Tokens.Table)
    default:
      // 未知 token 尝试渲染 text
      if ('text' in token) {
        return `<p>${renderInline(String((token as Tokens.Text).text))}</p>`
      }
      return ''
  }
}

function renderHeading(token: Tokens.Heading): string {
  const tag = `h${token.depth}`
  return `<${tag}>${renderInline(token.text)}</${tag}>`
}

function renderParagraph(token: Tokens.Paragraph): string {
  return `<p>${renderInline(token.text)}</p>`
}

function renderCode(token: Tokens.Code): string {
  const lang = token.lang || ''
  const escapedText = escapeHtml(token.text)
  // rich-text 组件使用 web 标签（div/span），不支持小程序专有标签（view 等）
  const langLabel = lang ? `<div class="code-lang">${escapeHtml(lang)}</div>` : ''
  return `<div class="code-block">${langLabel}<pre><code class="language-${escapeHtml(lang)}">${escapedText}</code></pre></div>`
}

function renderBlockquote(token: Tokens.Blockquote): string {
  let inner = ''
  for (const t of token.tokens) {
    inner += renderToken(t)
  }
  return `<blockquote>${inner}</blockquote>`
}

function renderList(token: Tokens.List): string {
  const tag = token.ordered ? 'ol' : 'ul'
  let items = ''
  for (const item of token.items) {
    items += renderListItem(item)
  }
  return `<${tag}>${items}</${tag}>`
}

/**
 * 渲染单个列表项
 *
 * 一个 item 只输出一个 <li>，内部拼接所有子 token：
 * - text / paragraph：按行内文本渲染（宽松列表的 item.tokens 为 paragraph）
 * - space：跳过
 * - 其它块级（嵌套 list、blockquote、code 等）：递归渲染
 */
function renderListItem(item: Tokens.ListItem): string {
  let inner = ''
  for (const t of item.tokens) {
    if (t.type === 'text') {
      inner += renderInline((t as Tokens.Text).text)
    } else if (t.type === 'paragraph') {
      inner += renderInline((t as Tokens.Paragraph).text)
    } else if (t.type === 'space') {
      // 忽略空白，避免生成空 <li>
    } else {
      inner += renderToken(t)
    }
  }
  return `<li>${inner}</li>`
}

/**
 * 安全提取表格单元格的文本
 *
 * marked v15 的 table cell 可能是字符串或对象（含 text 属性）。
 * 统一处理两种格式，避免 `src.substring is not a function` 错误。
 */
function extractCellText(cell: unknown): string {
  if (typeof cell === 'string') return cell
  if (cell && typeof cell === 'object' && 'text' in cell) {
    return String((cell as { text: unknown }).text)
  }
  return String(cell ?? '')
}

function renderTable(token: Tokens.Table): string {
  let html = '<table>'
  // 表头
  html += '<thead><tr>'
  for (const headerCell of token.header) {
    html += `<th>${renderInline(extractCellText(headerCell))}</th>`
  }
  html += '</tr></thead>'
  // 表体
  html += '<tbody>'
  for (const row of token.rows) {
    html += '<tr>'
    for (const cell of row) {
      html += `<td>${renderInline(extractCellText(cell))}</td>`
    }
    html += '</tr>'
  }
  html += '</tbody></table>'
  return html
}

/* ============ 行内渲染 ============ */

/**
 * 行内 Markdown 正则匹配表
 *
 * 弃用 marked.Lexer.lexInline：其 text token 的 tokens 属性会导致无限递归，
 * 且直接 escapeHtml 会丢失所有行内格式。
 * 改用正则直接匹配，无递归、可预测、保留格式。
 *
 * 顺序很重要：codespan 必须最先匹配，避免代码块内的 ** 等被误解析。
 */
const INLINE_RULES: Array<{ pattern: RegExp; render: (match: RegExpExecArray) => string }> = [
  // 行内代码 `code`（最先匹配，避免内部格式被误解析）
  {
    pattern: /`([^`]+?)`/g,
    render: (m) => `<code class="inline-code">${escapeHtml(m[1]!)}</code>`,
  },
  // 粗斜体 ***text*** 或 ___text___
  {
    pattern: /\*\*\*(.+?)\*\*\*/g,
    render: (m) => `<strong><em>${escapeHtml(m[1]!)}</em></strong>`,
  },
  // 粗体 **text** 或 __text__
  {
    pattern: /\*\*(.+?)\*\*/g,
    render: (m) => `<strong>${escapeHtml(m[1]!)}</strong>`,
  },
  // 斜体 *text* 或 _text_
  {
    pattern: /(?<!\w)\*([^*]+?)\*(?!\w)/g,
    render: (m) => `<em>${escapeHtml(m[1]!)}</em>`,
  },
  // 删除线 ~~text~~
  {
    pattern: /~~(.+?)~~/g,
    render: (m) => `<del>${escapeHtml(m[1]!)}</del>`,
  },
  // 图片 ![alt](url)
  {
    pattern: /!\[([^\]]*)\]\(([^)]+)\)/g,
    render: (m) => `<img src="${escapeHtml(m[2]!)}" alt="${escapeHtml(m[1]!)}" />`,
  },
  // 链接 [text](url)
  {
    pattern: /\[([^\]]+)\]\(([^)]+)\)/g,
    render: (m) =>
      `<a href="${escapeHtml(m[2]!)}" target="_blank" rel="noopener noreferrer">${escapeHtml(m[1]!)}</a>`,
  },
]

/**
 * 渲染行内 Markdown 为 HTML
 *
 * 采用正则驱动方案：依次用每条规则扫描文本，
 * 用占位符保护已匹配区域，最终拼装结果。
 * 无递归、无 lexInline 依赖，彻底避免栈溢出。
 */
function renderInline(text: string): string {
  if (!text) return ''

  // 将文本拆分为段：未匹配段用 escapeHtml，匹配段用规则渲染
  // 采用「逐步替换 + 占位符」策略，防止重复匹配
  const placeholders: string[] = []
  let result = text

  for (const rule of INLINE_RULES) {
    result = result.replace(rule.pattern, (...args) => {
      const match = args as unknown as RegExpExecArray
      const html = rule.render(match)
      const idx = placeholders.length
      placeholders.push(html)
      return `\x00PH${idx}\x00`
    })
  }

  // 转义剩余文本中的 HTML 特殊字符
  result = escapeHtml(result)

  // 还原占位符为实际 HTML
  result = result.replace(/\x00PH(\d+)\x00/g, (_, idx) => placeholders[Number(idx)]!)

  return result
}

/* ============ 工具函数 ============ */

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
