/// <reference types="@dcloudio/types" />

declare module '@dcloudio/uni-app' {
  type LifecycleCallback = (...args: any[]) => void
  /** 页面显示时触发 */
  export function onShow(callback: LifecycleCallback): void
  /** 页面加载时触发，query 为页面路由参数 */
  export function onLoad(callback: (query: Record<string, string | undefined>) => void): void
  /** 页面隐藏时触发 */
  export function onHide(callback: LifecycleCallback): void
  /** 页面卸载时触发 */
  export function onUnload(callback: LifecycleCallback): void
}

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  const component: DefineComponent<{}, {}, any>
  export default component
}
