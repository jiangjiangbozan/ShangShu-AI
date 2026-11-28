/// <reference types="@dcloudio/types" />
/// <reference types="vite/client" />

/**
 * Vite 注入的环境变量类型，避免把配置写成散落的魔法字符串
 */
interface ImportMetaEnv {
  /** API 基础地址，定义见 .env.development / .env.production */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '@dcloudio/uni-app' {
  type LifecycleCallback = (...args: unknown[]) => void
  interface ShareAppMessageResult {
    title?: string
    path?: string
    imageUrl?: string
  }
  interface ShareTimelineResult {
    title?: string
    query?: string
    imageUrl?: string
  }
  /** 页面显示时触发 */
  export function onShow(callback: LifecycleCallback): void
  /** 页面加载时触发，query 为页面路由参数 */
  export function onLoad(callback: (query: Record<string, string | undefined>) => void): void
  /** 页面隐藏时触发 */
  export function onHide(callback: LifecycleCallback): void
  /** 页面卸载时触发 */
  export function onUnload(callback: LifecycleCallback): void
  /** 分享给好友 */
  export function onShareAppMessage(callback: () => ShareAppMessageResult): void
  /** 分享到朋友圈 */
  export function onShareTimeline(callback: () => ShareTimelineResult): void
}

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
