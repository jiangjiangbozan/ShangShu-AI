<script setup lang="ts">
/**
 * 第三方 OAuth 回调页
 *
 * 后端在完成 Gitee 授权并签发令牌后，通过 302 重定向到本页，令牌经 URL fragment 传递
 * （形如 #accessToken=...&refreshToken=...&csrfToken=...），避免令牌脱敏信息进入访问日志。
 *
 * 本页职责：
 *  1. 解析 fragment 中的令牌；
 *  2. 成功：持久化令牌并拉取用户信息，跳转首页；
 *  3. 失败：提示错误并跳转登录页。
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/** 处理中标记 */
const loading = ref(true)
/** 错误信息（非空时展示） */
const error = ref('')

/**
 * 解析 URL fragment 为键值对象。
 * @param hash 形如 "#a=1&b=2" 的 fragment
 */
function parseHash(hash: string): Record<string, string> {
  const result: Record<string, string> = {}
  const raw = hash.replace(/^#/, '')
  if (!raw) return result
  for (const pair of raw.split('&')) {
    const idx = pair.indexOf('=')
    if (idx === -1) continue
    const key = decodeURIComponent(pair.slice(0, idx))
    const value = decodeURIComponent(pair.slice(idx + 1))
    result[key] = value
  }
  return result
}

onMounted(async () => {
  const params = parseHash(window.location.hash)
  // 清理 fragment，避免令牌残留于地址栏
  if (window.history?.replaceState) {
    window.history.replaceState(null, '', window.location.pathname)
  }

  const { accessToken, refreshToken, csrfToken, error: errCode } = params

  if (!accessToken || !refreshToken || !csrfToken) {
    error.value = errCode ? `第三方登录失败（错误码：${errCode}）` : '第三方登录失败，请重试'
    loading.value = false
    ElMessage.error(error.value)
    setTimeout(() => router.replace('/login'), 1500)
    return
  }

  try {
    authStore.setTokens(accessToken, refreshToken, csrfToken)
    await authStore.fetchCurrentUser()
    ElMessage.success('登录成功')
    router.replace('/')
  } catch {
    authStore.clearTokens()
    ElMessage.error('登录状态初始化失败，请重试')
    router.replace('/login')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="oauth-callback">
    <div v-if="loading" class="status">
      <p>正在完成第三方登录，请稍候…</p>
    </div>
    <div v-else class="status">
      <p>{{ error || '登录成功，正在跳转…' }}</p>
    </div>
  </div>
</template>

<style scoped>
.oauth-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 50%, #eff6ff 100%);
}

.status {
  font-size: var(--font-size-md);
  color: var(--text-secondary);
}
</style>