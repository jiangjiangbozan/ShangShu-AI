/**
 * 暗色模式 composable
 *
 * 通过给 <html> 添加/移除 .dark 触发 Element Plus dark 变量与 design-tokens.css 中
 * html.dark 覆盖；偏好持久化到 localStorage，首次访问跟随系统 prefers-color-scheme。
 */
import { computed, ref } from 'vue'

const STORAGE_KEY = 'ai-platform-theme'

type ThemeMode = 'light' | 'dark'

function initialTheme(): ThemeMode {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

const isDark = ref<boolean>(initialTheme() === 'dark')

/** 应用主题到 <html> */
function applyTheme(dark: boolean) {
  isDark.value = dark
  document.documentElement.classList.toggle('dark', dark)
  localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light')
}

/** 切换主题 */
function toggleTheme() {
  applyTheme(!isDark.value)
}

/** 设置为指定主题 */
function setTheme(mode: ThemeMode) {
  applyTheme(mode === 'dark')
}

const theme = computed<ThemeMode>(() => (isDark.value ? 'dark' : 'light'))

// 初始化（幂等，刷新后保持）
if (typeof document !== 'undefined') {
  applyTheme(initialTheme() === 'dark')
}

export function useTheme() {
  return { isDark, theme, toggleTheme, setTheme }
}
