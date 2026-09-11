<script setup lang="ts">
/**
 * 默认布局
 *
 * 设计理念：
 * - 现代管理后台布局：可折叠侧边栏 + 顶栏 + 面包屑 + 主内容区
 * - 渐变品牌色 + 玻璃质感（backdrop-filter）
 * - 响应式：移动端抽屉式侧边栏，桌面端固定侧边栏
 * - 暗色模式预留
 */
import { computed, ref, shallowRef, markRaw, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElContainer,
  ElAside,
  ElHeader,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElSubMenu,
  ElIcon,
  ElButton,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElTooltip,
  ElDrawer,
  ElAvatar,
  ElBadge,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElDivider,
  ElMessage,
  ElPopover,
} from 'element-plus'
import {
  ChatDotRound,
  Setting,
  DataAnalysis,
  ChatLineSquare,
  Cpu,
  Fold,
  Expand,
  Bell,
  Search,
  FullScreen,
  ScaleToOriginal,
  Moon,
  Sunny,
  User,
  SwitchButton,
  Key,
  UserFilled,
  Monitor,
  Document,
  DataBoard,
  Odometer,
  Memo,
  CircleCheck,
  Warning,
  InfoFilled,
  EditPen,
  Share,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { isDark, toggleTheme } = useTheme()

/** 当前登录用户名（store.user 已存在则取，否则回退占位） */
const userName = computed(() => authStore.user?.username || authStore.user?.email || '用户')

/** 跳转登录 */
function goLogin() {
  router.push({
    path: '/login',
    query: { redirect: route.fullPath },
  })
}

/** 跳转个人中心 */
function goProfile() {
  router.push('/profile')
}

/** 下拉菜单项点击 */
async function handleUserCommand(command: 'profile' | 'logout') {
  if (command === 'profile') {
    goProfile()
  } else if (command === 'logout') {
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

/** 侧边栏折叠状态（桌面端） */
const isCollapsed = ref(false)

/** 移动端抽屉可见性 */
const isDrawerVisible = ref(false)

/** 菜单叶子节点 */
interface MenuLeaf {
  index: string
  title: string
  icon: typeof ChatDotRound
  disabled?: boolean
  badge?: string
  /** 系统管理子菜单所需的权限点（RBAC） */
  perm?: string
}

/** 菜单分组（父菜单） */
interface MenuGroup {
  index: string
  title: string
  icon: typeof ChatDotRound
  children: MenuLeaf[]
}

/** 分组菜单定义（两层结构：分组 → 页面） */
const menuGroups = shallowRef<MenuGroup[]>([
  {
    index: 'group-dashboard',
    title: '工作台',
    icon: markRaw(DataBoard),
    children: [
      { index: '/dashboard', title: '平台仪表盘', icon: markRaw(DataBoard) },
      { index: '/chat-search', title: '全局搜索', icon: markRaw(Search) },
    ],
  },
  {
    index: 'group-chat',
    title: '对话与智能',
    icon: markRaw(ChatLineSquare),
    children: [
      { index: '/chat', title: '对话聊天', icon: markRaw(ChatLineSquare) },
      { index: '/model-test', title: '模型测试', icon: markRaw(ChatDotRound) },
      { index: '/agent', title: '任务 Agent', icon: markRaw(Cpu) },
      { index: '/customer-service', title: '智能客服', icon: markRaw(ChatDotRound) },
      { index: '/data-analysis', title: '数据分析', icon: markRaw(DataAnalysis) },
      { index: '/workflow', title: '工作流', icon: markRaw(Share) },
      { index: '/conversation-logs', title: '对话日志', icon: markRaw(Memo) },
    ],
  },
  {
    index: 'group-model',
    title: '模型与知识',
    icon: markRaw(Cpu),
    children: [
      { index: '/model-configs', title: '模型配置', icon: markRaw(Setting) },
      { index: '/model-routes', title: '模型路由/降级', icon: markRaw(Document) },
      { index: '/rag', title: '知识库 RAG', icon: markRaw(DataAnalysis) },
    ],
  },
  {
    index: 'group-govern',
    title: '平台治理',
    icon: markRaw(Odometer),
    children: [
      { index: '/prompt-templates', title: 'Prompt 模板', icon: markRaw(Document) },
      { index: '/limits', title: '限额/限流', icon: markRaw(Odometer) },
      { index: '/moderation', title: '内容审查', icon: markRaw(Key) },
      { index: '/annotations', title: '标注问答', icon: markRaw(EditPen) },
      { index: '/agent-eval', title: 'Agent 评测', icon: markRaw(DataBoard) },
      { index: '/audit-logs', title: '操作审计', icon: markRaw(Memo) },
      { index: '/system/ops', title: '系统运维', icon: markRaw(Monitor) },
      { index: '/system/api-keys', title: 'API 密钥', icon: markRaw(Key) },
      { index: '/developer/api-docs', title: 'API 调用示例', icon: markRaw(Document) },
    ],
  },
])

/** 系统管理（RBAC）子菜单：按菜单权限点动态渲染 */
const systemMenuItems = computed<MenuLeaf[]>(() =>
  [
    { index: '/system/users', title: '用户管理', icon: markRaw(UserFilled), perm: 'system:user:menu' },
    { index: '/system/roles', title: '角色管理', icon: markRaw(User), perm: 'system:role:menu' },
    { index: '/system/permissions', title: '权限管理', icon: markRaw(Key), perm: 'system:permission:menu' },
  ].filter((m) => authStore.hasPerm(m.perm)),
)

/** 当前激活菜单 */
const activeMenu = computed(() => route.path)

/** 面包屑项 */
const breadcrumbs = computed(() => {
  const items: { title: string; path?: string }[] = [{ title: '首页', path: '/' }]
  const matched = route.matched.filter((r) => r.meta?.title)
  matched.forEach((r) => {
    items.push({ title: r.meta.title as string, path: r.path })
  })
  return items
})

/** 切换侧边栏折叠 */
function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

/** 处理菜单点击（移动端关闭抽屉） */
function handleMenuSelect() {
  isDrawerVisible.value = false
}

/** 响应式：当前是否为桌面端 */
const isDesktop = ref(typeof window !== 'undefined' ? window.innerWidth >= 1024 : true)
function handleResize() {
  isDesktop.value = window.innerWidth >= 1024
  if (isDesktop.value) isDrawerVisible.value = false
}
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => window.removeEventListener('resize', handleResize))

/* ============ 通知中心 ============ */
interface Notice {
  id: number
  type: 'system' | 'success' | 'warn' | 'info'
  title: string
  time: string
  read: boolean
}

const notices = ref<Notice[]>([
  { id: 1, type: 'system', title: '平台将于周日 02:00-04:00 进行例行维护，期间对话服务可能短暂中断。', time: '10 分钟前', read: false },
  { id: 2, type: 'warn', title: '模型路由检测到 gemini-pro 连续 3 次超时，已自动降级至备选模型。', time: '1 小时前', read: false },
  { id: 3, type: 'success', title: '新知识库「产品 FAQ」文档同步完成，共处理 128 个分片。', time: '3 小时前', read: false },
  { id: 4, type: 'info', title: '审计日志发现一次异常登录尝试，请及时核对账号安全。', time: '昨天', read: true },
  { id: 5, type: 'system', title: '新增内容审查策略，对话消息已接入 Moderation 前置检查。', time: '昨天', read: true },
])

const unreadCount = computed(() => notices.value.filter((n) => !n.read).length)

function markAllRead() {
  notices.value.forEach((n) => (n.read = true))
}

/** 通知类型图标 */
function noticeIcon(type: Notice['type']) {
  if (type === 'success') return CircleCheck
  if (type === 'warn') return Warning
  if (type === 'info') return InfoFilled
  return InfoFilled
}

/* ============ 全屏切换 ============ */
const isFullscreen = ref(false)
function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => {})
  } else {
    document.documentElement.requestFullscreen().catch(() => {})
  }
}
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}
onMounted(() => document.addEventListener('fullscreenchange', onFullscreenChange))
onUnmounted(() => document.removeEventListener('fullscreenchange', onFullscreenChange))
</script>

<template>
  <el-container class="default-layout">
    <!-- ============ 桌面端侧边栏 ============ -->
    <el-aside
      v-if="isDesktop"
      :width="isCollapsed ? 'var(--sidebar-collapsed-width)' : 'var(--sidebar-width)'"
      class="layout-aside"
      :class="{ 'is-collapsed': isCollapsed }"
    >
      <div class="logo" :class="{ 'is-collapsed': isCollapsed }">
        <div class="logo-icon">
          <el-icon :size="22"><Cpu /></el-icon>
        </div>
        <transition name="fade-slide">
          <div v-show="!isCollapsed" class="logo-text">
            <div class="logo-title">AI 开放平台</div>
            <div class="logo-subtitle">Intelligent Assistant</div>
          </div>
        </transition>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        router
        class="layout-menu"
        @select="handleMenuSelect"
      >
        <el-sub-menu
          v-for="group in menuGroups"
          :key="group.index"
          :index="group.index"
        >
          <template #title>
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item
            v-for="item in group.children"
            :key="item.index"
            :index="item.index"
            :disabled="item.disabled"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              <span>{{ item.title }}</span>
              <el-badge
                v-if="item.badge"
                :value="item.badge"
                type="info"
                class="menu-badge"
              />
            </template>
          </el-menu-item>
        </el-sub-menu>
        <!-- 系统管理（RBAC）子菜单 -->
        <el-sub-menu v-if="systemMenuItems.length" index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item
            v-for="item in systemMenuItems"
            :key="item.index"
            :index="item.index"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <!-- 折叠按钮 -->
      <div class="collapse-trigger" @click="toggleCollapse">
        <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
        <transition name="fade-slide">
          <span v-show="!isCollapsed" class="collapse-text">收起菜单</span>
        </transition>
      </div>
    </el-aside>

    <!-- ============ 移动端抽屉式侧边栏 ============ -->
    <el-drawer
      v-model="isDrawerVisible"
      direction="ltr"
      :with-header="false"
      size="240px"
      class="mobile-drawer"
    >
      <div class="logo">
        <div class="logo-icon"><el-icon :size="22"><Cpu /></el-icon></div>
        <div class="logo-text">
          <div class="logo-title">AI 开放平台</div>
          <div class="logo-subtitle">Intelligent Assistant</div>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="layout-menu" @select="handleMenuSelect">
        <el-sub-menu
          v-for="group in menuGroups"
          :key="group.index"
          :index="group.index"
        >
          <template #title>
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item
            v-for="item in group.children"
            :key="item.index"
            :index="item.index"
            :disabled="item.disabled"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              <span>{{ item.title }}</span>
              <el-badge v-if="item.badge" :value="item.badge" type="info" class="menu-badge" />
            </template>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="systemMenuItems.length" index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item
            v-for="item in systemMenuItems"
            :key="item.index"
            :index="item.index"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-drawer>

    <!-- ============ 主区域 ============ -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-button
            v-if="!isDesktop"
            text
            :icon="Expand"
            @click="isDrawerVisible = true"
          />
          <el-button
            v-else
            text
            :icon="isCollapsed ? Expand : Fold"
            @click="toggleCollapse"
          />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(crumb, idx) in breadcrumbs"
              :key="idx"
              :to="crumb.path ? { path: crumb.path } : undefined"
            >
              {{ crumb.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-tooltip content="搜索（Ctrl+K）" placement="bottom">
            <el-button text circle :icon="Search" @click="router.push('/chat-search')" />
          </el-tooltip>

          <el-popover
            placement="bottom-end"
            :width="360"
            trigger="click"
            popper-class="notice-popover"
          >
            <template #reference>
              <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0">
                <el-button text circle :icon="Bell" />
              </el-badge>
            </template>
            <div class="notice-panel">
              <div class="notice-header">
                <span class="notice-title">通知中心</span>
                <el-button text type="primary" size="small" :disabled="!unreadCount" @click="markAllRead">
                  全部已读
                </el-button>
              </div>
              <div v-if="!notices.length" class="notice-empty">暂无通知</div>
              <div v-else class="notice-list">
                <div
                  v-for="n in notices"
                  :key="n.id"
                  class="notice-item"
                  :class="{ unread: !n.read }"
                >
                  <el-icon class="notice-dot" :class="n.type">
                    <component :is="noticeIcon(n.type)" />
                  </el-icon>
                  <div class="notice-content">
                    <div class="notice-text">{{ n.title }}</div>
                    <div class="notice-time">{{ n.time }}</div>
                  </div>
                </div>
              </div>
            </div>
          </el-popover>

          <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="bottom">
            <el-button
              text
              circle
              :icon="isFullscreen ? ScaleToOriginal : FullScreen"
              @click="toggleFullscreen"
            />
          </el-tooltip>

          <el-tooltip :content="isDark ? '切换到亮色模式' : '切换到暗色模式'" placement="bottom">
            <el-button text circle :icon="isDark ? Sunny : Moon" @click="toggleTheme" />
          </el-tooltip>

          <el-divider direction="vertical" />
          <!-- 已登录：用户下拉菜单 -->
          <el-dropdown
            v-if="authStore.isAuthenticated"
            trigger="click"
            placement="bottom-end"
            @command="handleUserCommand"
          >
            <div class="user-chip">
              <el-avatar :size="32" class="user-avatar">
                {{ userName.charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="user-info">
                <div class="user-name">{{ userName }}</div>
                <div class="user-role">已登录</div>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="User" command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item :icon="SwitchButton" command="logout" divided>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 未登录：登录按钮 -->
          <el-button v-else type="primary" plain size="small" :icon="User" @click="goLogin">
            登录
          </el-button>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <div class="content-wrapper">
          <router-view v-slot="{ Component }">
            <transition name="page" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.default-layout {
  height: 100vh;
  overflow: hidden;
}

/* ============ 侧边栏 ============ */
.layout-aside {
  background: var(--sidebar-bg);
  color: var(--sidebar-text);
  transition: width var(--transition-base);
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--sidebar-border);
  box-shadow: 1px 0 4px rgb(0 0 0 / 0.04);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  height: var(--header-height);
  padding: 0 20px;
  border-bottom: 1px solid var(--sidebar-border);
  overflow: hidden;
  white-space: nowrap;
}
.logo.is-collapsed {
  justify-content: center;
  padding: 0;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--sidebar-logo-icon-from), var(--sidebar-logo-icon-to));
  border-radius: var(--radius-lg);
  color: #fff;
  box-shadow: 0 4px 12px rgb(99 102 241 / 0.25);
  flex-shrink: 0;
}

.logo-text {
  overflow: hidden;
}
.logo-title {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
  color: var(--sidebar-logo-title);
  line-height: 1.2;
}
.logo-subtitle {
  font-size: 11px;
  color: var(--sidebar-logo-subtitle);
  margin-top: 2px;
}

/* 菜单 */
.layout-menu {
  flex: 1;
  border-right: none;
  background: transparent !important;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--sidebar-text);
  --el-menu-active-color: var(--sidebar-text-active);
  --el-menu-hover-bg-color: var(--sidebar-hover-bg);
  --el-menu-item-height: 48px;
}

.layout-menu:not(.el-menu--collapse) {
  width: 100%;
  padding: 12px 8px;
}

.layout-menu :deep(.el-menu-item) {
  border-radius: var(--radius-md);
  margin: 4px 0;
  height: 44px;
  line-height: 44px;
  color: var(--sidebar-text);
  transition: all var(--transition-fast);
}

.layout-menu :deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
  color: var(--sidebar-text-muted);
  border-radius: var(--radius-md);
  margin: 4px 0;
  transition: all var(--transition-fast);
}

.layout-menu :deep(.el-sub-menu__title:hover) {
  background-color: var(--sidebar-hover-bg) !important;
  color: var(--sidebar-text-active);
}

.layout-menu :deep(.el-menu-item:hover) {
  background-color: var(--sidebar-hover-bg) !important;
  color: var(--sidebar-text-active);
}

.layout-menu :deep(.el-menu-item.is-active) {
  background: var(--sidebar-active-bg) !important;
  color: var(--sidebar-text-active);
  box-shadow: inset 3px 0 0 var(--sidebar-active-border);
}

/* 子菜单弹出层（折叠态）沿用侧边栏风格 */
.layout-menu :deep(.el-menu--popup) {
  background: var(--sidebar-popup-bg) !important;
  border: 1px solid var(--sidebar-border);
  border-radius: var(--radius-lg);
  padding: 6px;
}
.layout-menu :deep(.el-menu--popup .el-menu-item) {
  color: var(--sidebar-text);
  background: transparent;
  border-radius: var(--radius-md);
}
.layout-menu :deep(.el-menu--popup .el-menu-item:hover) {
  background-color: var(--sidebar-hover-bg) !important;
  color: var(--sidebar-text-active);
}
.layout-menu :deep(.el-menu--popup .el-menu-item.is-active) {
  background: var(--sidebar-active-bg) !important;
  color: var(--sidebar-text-active);
}

.layout-menu :deep(.el-menu-item.is-disabled) {
  opacity: 0.45;
}

.menu-badge {
  margin-left: 8px;
}

/* 折叠触发器 */
.collapse-trigger {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 48px;
  padding: 0 20px;
  cursor: pointer;
  color: var(--sidebar-collapse-text);
  border-top: 1px solid var(--sidebar-border);
  transition: all var(--transition-fast);
  white-space: nowrap;
  overflow: hidden;
}
.collapse-trigger:hover {
  background-color: var(--sidebar-hover-bg);
  color: var(--sidebar-text-active);
}
.layout-aside.is-collapsed .collapse-trigger {
  justify-content: center;
  padding: 0;
}
.collapse-text {
  font-size: 13px;
  color: var(--sidebar-collapse-text);
}

/* ============ 顶栏 ============ */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height) !important;
  padding: 0 var(--space-5) !important;
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--border-default);
  box-shadow: var(--shadow-xs);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}
.user-chip:hover {
  background-color: var(--bg-subtle);
}

.user-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-weight: var(--font-weight-semibold);
}

.user-info {
  line-height: 1.2;
}
.user-name {
  font-size: 13px;
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
}
.user-role {
  font-size: 11px;
  color: var(--text-tertiary);
}

/* ============ 主内容区 ============ */
.layout-main {
  background-color: var(--bg-page);
  padding: 0 !important;
  overflow-y: auto;
}

.content-wrapper {
  max-width: var(--content-max-width);
  margin: 0 auto;
  padding: var(--space-6);
}

/* ============ 动画 ============ */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all var(--transition-fast);
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(-8px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

.page-enter-active,
.page-leave-active {
  transition: all var(--transition-base);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ============ 响应式 ============ */
@media (max-width: 768px) {
  .header-right .user-info {
    display: none;
  }
  .content-wrapper {
    padding: var(--space-4);
  }
}

@media (max-width: 480px) {
  .header-right .el-tooltip + .el-tooltip,
  .header-right > .el-button + .el-button {
    display: none;
  }
}
</style>

<style>
/* 移动端抽屉样式覆盖 */
.mobile-drawer .el-drawer__body {
  padding: 0;
  background: var(--sidebar-popup-bg);
}

/* ============ 通知中心弹层（挂载于 body，需全局样式） ============ */
.notice-popover {
  --el-popover-padding: 0;
  border-radius: var(--radius-lg);
}
.notice-panel {
  display: flex;
  flex-direction: column;
}
.notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-default);
}
.notice-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}
.notice-list {
  max-height: 360px;
  overflow-y: auto;
  padding: var(--space-2);
}
.notice-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);
}
.notice-item:hover {
  background-color: var(--bg-subtle);
}
.notice-dot {
  font-size: 16px;
  margin-top: 2px;
  flex-shrink: 0;
}
.notice-dot.system,
.notice-dot.info {
  color: var(--color-info);
}
.notice-dot.success {
  color: var(--color-success);
}
.notice-dot.warn {
  color: var(--color-warning);
}
.notice-content {
  flex: 1;
  min-width: 0;
}
.notice-text {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.5;
}
.notice-item.unread .notice-text {
  color: var(--text-primary);
  font-weight: var(--font-weight-medium);
}
.notice-time {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-top: 4px;
}
.notice-empty {
  padding: var(--space-8);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
