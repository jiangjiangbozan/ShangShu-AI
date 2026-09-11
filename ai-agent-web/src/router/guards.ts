/**
 * 路由守卫
 *
 * - beforeEach：meta.public 为 true 放行；否则先向后端校验登录态（应用启动即验证），
 *   未认证时跳转登录页（带 redirect）
 * - afterEach：根据 meta.title 设置 document.title
 */
import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/** 站点标题后缀 */
const SITE_TITLE_SUFFIX = 'AI 开放平台'
/** 默认站点标题 */
const SITE_TITLE = 'AI 开放平台'

/**
 * 安装路由守卫
 * @param router 路由实例
 */
export function setupRouterGuards(router: Router): void {
  // ============ 前置守卫 ============
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()

    // 公开路由（登录/注册/忘记密码/重置密码）直接放行
    if (to.meta.public === true) {
      return true
    }

    // 首次进入受保护页面时，主动向后端校验登录态（幂等），
    // 确保应用启动即进行令牌有效性验证，而非等到具体请求才被 401 拦截
    await authStore.initialize()

    // 未认证：跳转登录页，携带回跳地址
    if (!authStore.isAuthenticated) {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
      }
    }

    // 对声明了 meta.perm 的路由做权限点校验（RBAC）
    const requiredPerm = to.meta?.perm
    if (typeof requiredPerm === 'string' && !authStore.hasPerm(requiredPerm)) {
      return { path: '/403' }
    }

    return true
  })

  // ============ 后置钩子：设置页面标题 ============
  router.afterEach((to) => {
    const title = to.meta?.title
    if (typeof document !== 'undefined') {
      document.title = title ? `${title} - ${SITE_TITLE_SUFFIX}` : SITE_TITLE
    }
  })
}
