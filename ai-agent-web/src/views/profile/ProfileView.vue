<script setup lang="ts">
/**
 * 个人中心页面
 *
 * 功能：
 * - 用户基本信息展示
 * - Token 用量统计
 * - 会话统计概览
 * - 账号安全设置
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User,
  Clock,
  DataLine,
  TrendCharts,
  Key,
  Lock,
  CopyDocument,
  View,
  Hide,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getTokenStats, listSessions, type SessionResp } from '@/api/chat'
import { getCurrentUser } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

defineOptions({ name: 'ProfileView' })

/** 用户信息 */
const userName = computed(() => authStore.user?.username || '用户')
const userEmail = computed(() => authStore.user?.email || '')
const lastLoginAt = computed(() => authStore.user?.lastLoginAt || '')

/** 是否正在加载 */
const loading = ref(false)

/** Token 统计 */
interface TokenStats {
  period: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
}
const tokenStats = ref<TokenStats | null>(null)
const tokenPeriod = ref<'today' | 'week' | 'month'>('today')

/** 会话统计 */
const sessionCount = ref(0)
const totalMessages = ref(0)

/** 显示/隐藏 API Key */
const apiKeyVisible = ref(false)

/** 加载统计数据 */
async function loadStats() {
  loading.value = true
  try {
    // 并行加载 Token 统计和会话统计（游标分页：取最新一屏统计）
    const [stats, sessionPage] = await Promise.all([
      getTokenStats(tokenPeriod.value),
      listSessions({ limit: 100 }),
    ])
    tokenStats.value = stats as unknown as TokenStats
    sessionCount.value = sessionPage.data.length
    totalMessages.value = sessionPage.data.reduce(
      (sum: number, session: SessionResp) => sum + session.messageCount,
      0,
    )
  } catch (e) {
    // 统计加载失败不影响页面展示
  } finally {
    loading.value = false
  }
}

/** 切换统计周期 */
function switchPeriod(period: 'today' | 'week' | 'month') {
  tokenPeriod.value = period
  loadStats()
}

/** 复制 API Key */
function copyApiKey() {
  const token = authStore.accessToken
  if (token) {
    navigator.clipboard.writeText(token).then(() => {
      ElMessage.success('已复制到剪贴板')
    })
  }
}

/** 退出登录 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

/** 格式化数字 */
function formatNumber(n: number): string {
  if (n >= 10000) return (n / 1000).toFixed(1) + 'k'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

/** 格式化日期 */
function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(() => {
  loadStats()
  // 确保用户信息是最新的
  authStore.fetchCurrentUser()
})
</script>

<template>
  <div class="profile-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>个人中心</h2>
      <span class="page-subtitle">管理您的账户信息和查看使用统计</span>
    </div>

    <div class="profile-grid">
      <!-- 左侧：用户信息卡片 -->
      <div class="profile-card user-card">
        <div class="card-header">
          <el-icon :size="18"><User /></el-icon>
          <span>基本信息</span>
        </div>
        <div class="card-body">
          <div class="avatar-section">
            <div class="avatar-circle">
              {{ userName.charAt(0).toUpperCase() }}
            </div>
            <div class="avatar-info">
              <div class="display-name">{{ userName }}</div>
              <div class="display-role">普通用户</div>
            </div>
          </div>
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">邮箱</span>
              <span class="info-value">{{ userEmail || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">
                <el-icon :size="14"><Clock /></el-icon>
                最后登录
              </span>
              <span class="info-value">{{ formatDate(lastLoginAt) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">用户 ID</span>
              <span class="info-value">{{ authStore.user?.userId || '-' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧上：Token 统计 -->
      <div class="profile-card stats-card">
        <div class="card-header">
          <el-icon :size="18"><DataLine /></el-icon>
          <span>Token 用量</span>
          <div class="period-switch">
            <el-button
              :type="tokenPeriod === 'today' ? 'primary' : 'default'"
              size="small"
              plain
              @click="switchPeriod('today')"
            >今日</el-button>
            <el-button
              :type="tokenPeriod === 'week' ? 'primary' : 'default'"
              size="small"
              plain
              @click="switchPeriod('week')"
            >本周</el-button>
            <el-button
              :type="tokenPeriod === 'month' ? 'primary' : 'default'"
              size="small"
              plain
              @click="switchPeriod('month')"
            >本月</el-button>
          </div>
        </div>
        <div class="card-body">
          <div v-if="loading" class="loading-placeholder">
            <el-icon class="is-loading" :size="24"><DataLine /></el-icon>
          </div>
          <div v-else-if="tokenStats" class="stats-row">
            <div class="stat-item">
              <div class="stat-value">{{ formatNumber(tokenStats.totalTokens) }}</div>
              <div class="stat-label">总 Token</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-value">{{ formatNumber(tokenStats.promptTokens) }}</div>
              <div class="stat-label">输入 Token</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-value">{{ formatNumber(tokenStats.completionTokens) }}</div>
              <div class="stat-label">输出 Token</div>
            </div>
          </div>
          <div v-else class="empty-state">
            暂无统计数据
          </div>
        </div>
      </div>

      <!-- 右侧下：会话统计 -->
      <div class="profile-card session-stats-card">
        <div class="card-header">
          <el-icon :size="18"><TrendCharts /></el-icon>
          <span>会话概览</span>
        </div>
        <div class="card-body">
          <div class="stats-row">
            <div class="stat-item">
              <div class="stat-value">{{ sessionCount }}</div>
              <div class="stat-label">会话数</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-value">{{ totalMessages }}</div>
              <div class="stat-label">消息数</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 左侧下：安全设置 -->
      <div class="profile-card security-card">
        <div class="card-header">
          <el-icon :size="18"><Lock /></el-icon>
          <span>安全设置</span>
        </div>
        <div class="card-body">
          <div class="security-item">
            <div class="security-left">
              <el-icon :size="16"><Key /></el-icon>
              <div>
                <div class="security-title">API 访问令牌</div>
                <div class="security-desc">
                  {{ apiKeyVisible ? authStore.accessToken?.substring(0, 20) + '...' : '••••••••••••••••••••••••' }}
                </div>
              </div>
            </div>
            <div class="security-right">
              <el-button text :icon="apiKeyVisible ? Hide : View" @click="apiKeyVisible = !apiKeyVisible">
                {{ apiKeyVisible ? '隐藏' : '显示' }}
              </el-button>
              <el-button text :icon="CopyDocument" @click="copyApiKey">复制</el-button>
            </div>
          </div>
          <div class="security-item">
            <div class="security-left">
              <el-icon :size="16"><Lock /></el-icon>
              <div>
                <div class="security-title">账号安全</div>
                <div class="security-desc">保护您的账户安全</div>
              </div>
            </div>
            <div class="security-right">
              <el-button text type="danger" :icon="Lock" @click="handleLogout">退出登录</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}
.page-subtitle {
  font-size: 13px;
  color: var(--text-tertiary);
}

.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.profile-card {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-subtle);
}

.card-body {
  padding: 20px;
}

/* 用户卡片 */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.avatar-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 600;
  flex-shrink: 0;
}
.display-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.display-role {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.info-item:last-child {
  border-bottom: none;
}
.info-label {
  font-size: 13px;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.info-value {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

/* 统计卡片 */
.stats-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
}
.stat-item {
  text-align: center;
  flex: 1;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}
.stat-divider {
  width: 1px;
  height: 40px;
  background: var(--border-subtle);
}

.loading-placeholder {
  display: flex;
  justify-content: center;
  padding: 20px;
}
.empty-state {
  text-align: center;
  color: var(--text-tertiary);
  padding: 20px;
  font-size: 13px;
}

/* 周期切换 */
.period-switch {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

/* 安全设置 */
.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.security-item:last-child {
  border-bottom: none;
}
.security-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.security-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}
.security-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
  font-family: monospace;
}

/* 响应式 */
@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>