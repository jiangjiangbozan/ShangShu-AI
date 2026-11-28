/**
 * uni-app Vue3 + Vite CLI 构建配置
 *
 * 依赖用途：
 * - @dcloudio/uni-app、@dcloudio/uni-components、@dcloudio/uni-mp-weixin：
 *   uni-app 运行时与微信小程序平台实现，必须使用同一版本号避免编译产物错配。
 * - @dcloudio/vite-plugin-uni：官方 Vite 插件，负责 Vue SFC 编译与小程序产物生成。
 * - vite：构建工具，版本按插件 peerDependencies 固定为 5.2.8。
 * - cross-env：跨平台设置 UNI_INPUT_DIR，让 CLI 识别根目录平铺的源码结构。
 * - sass：编译组件中 lang="scss" 的样式块。
 */
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
})
