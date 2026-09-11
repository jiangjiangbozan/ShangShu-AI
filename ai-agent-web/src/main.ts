import { createApp } from 'vue'
import { createPinia } from 'pinia'

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

// 安装路由守卫（必须在 pinia 与 router 注册之后，守卫内会调用 useAuthStore）
setupRouterGuards(router)

app.mount('#app')
