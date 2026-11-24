import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// Element Plus 全局样式（必须在 App.vue 之前引入，否则组件无视觉样式）
import 'element-plus/dist/index.css'
// Element Plus 暗色模式基础变量（预留，后续可切换）
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'
// 路由守卫：未登录跳转 /login、设置 document.title
import { setupRouterGuards } from './router/guards'
// 全局设计令牌（颜色、排版、间距、响应式断点）
import './styles/design-tokens.css'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)

// 注册 Element Plus 组件库（此前缺失导致模板中的 <el-*> 均渲染为
// 未注册的自定义元素：按钮失去外观、图标失去尺寸约束、输入框不可见）
app.use(ElementPlus, { locale: zhCn })

// 安装路由守卫（必须在 pinia 与 router 注册之后，守卫内会调用 useAuthStore）
setupRouterGuards(router)

app.mount('#app')
