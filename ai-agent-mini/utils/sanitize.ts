/**
 * HTML 净化工具（小程序适配版）
 *
 * 小程序 rich-text 组件支持的标签有限，且无 DOM 环境无法使用 DOMPurify。
 * 本工具通过白名单机制过滤 HTML 标签和属性，防范 XSS 注入。
 *
 * 使用场景：Markdown 解析后、传入 rich-text 前进行净化。
 */

/** rich-text 组件允许的标签白名单 */
const ALLOWED_TAGS = new Set([
  'p', 'br', 'hr',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'blockquote', 'ul', 'ol', 'li',
  'pre', 'code',
  'em', 'strong', 'del', 's',
  'a', 'img',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'span', 'div',
  'sup', 'sub',
])

/** 允许的属性白名单 */
const ALLOWED_ATTRS = new Set([
  'href', 'src', 'alt', 'title', 'class', 'target', 'rel',
])

/**
 * 净化 HTML 字符串
 * @param html 待净化的 HTML
 * @returns 净化后的安全 HTML
 */
export function sanitizeHtml(html: string): string {
  if (!html) return ''

  // 移除 script / style / iframe 等危险标签及其内容
  let result = html.replace(
    /<(script|style|iframe|object|embed|form|input)[^>]*>[\s\S]*?<\/\1>/gi,
    '',
  )
  // 移除自闭合的危险标签
  result = result.replace(/<(script|style|iframe|object|embed|form|input)[^>]*\/?>/gi, '')

  // 逐标签过滤
  result = result.replace(/<\/?([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>/g, (match, tag, attrs) => {
    const tagLower = tag.toLowerCase()
    if (!ALLOWED_TAGS.has(tagLower)) return ''

    // 过滤属性
    const safeAttrs = filterAttributes(attrs)
    const isClosing = match.startsWith('</')
    if (isClosing) return `</${tagLower}>`
    const isSelfClosing = match.endsWith('/>')
    if (isSelfClosing) {
      return safeAttrs ? `<${tagLower}${safeAttrs} />` : `<${tagLower} />`
    }
    return safeAttrs ? `<${tagLower}${safeAttrs}>` : `<${tagLower}>`
  })

  return result
}

/** 过滤属性：只保留白名单内的属性，移除事件处理和 javascript: 协议 */
function filterAttributes(attrString: string): string {
  if (!attrString || !attrString.trim()) return ''

  const safeParts: string[] = []
  const attrRegex = /([a-zA-Z-]+)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|(\S+)))?/g
  let attrMatch: RegExpExecArray | null

  while ((attrMatch = attrRegex.exec(attrString)) !== null) {
    const name = attrMatch[1].toLowerCase()
    const value = attrMatch[2] ?? attrMatch[3] ?? attrMatch[4] ?? ''

    if (!ALLOWED_ATTRS.has(name)) continue
    // 禁止事件处理属性
    if (name.startsWith('on')) continue
    // 禁止 javascript: 协议
    if ((name === 'href' || name === 'src') && /^\s*javascript:/i.test(value)) continue

    safeParts.push(`${name}="${escapeAttrValue(value)}"`)
  }

  return safeParts.length > 0 ? ' ' + safeParts.join(' ') : ''
}

/** 转义属性值中的特殊字符 */
function escapeAttrValue(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
