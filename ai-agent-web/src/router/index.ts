import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: DefaultLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '平台仪表盘' },
        },
        {
          path: 'model-test',
          name: 'model-test',
          component: () => import('@/views/model-test/Index.vue'),
          meta: { title: '模型测试' },
        },
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/chat/ChatView.vue'),
          meta: { title: '对话聊天' },
        },
        {
          path: 'model-configs',
          name: 'model-configs',
          component: () => import('@/views/model-config/ModelConfigView.vue'),
          meta: { title: '模型配置' },
        },
        {
          path: 'workflow',
          name: 'workflow-list',
          component: () => import('@/views/workflow/WorkflowListView.vue'),
          meta: { title: '工作流' },
        },
        {
          path: 'workflow/:id',
          name: 'workflow-editor',
          component: () => import('@/views/workflow/WorkflowEditor.vue'),
          meta: { title: '工作流编辑器' },
        },
        {
          path: 'rag',
          name: 'rag',
          component: () => import('@/views/rag/KnowledgeBaseView.vue'),
          meta: { title: '知识库 RAG' },
        },
        {
          path: 'rag/feedback',
          name: 'rag-feedback',
          component: () => import('@/views/rag/FeedbackLogsView.vue'),
          meta: { title: '反馈日志' },
        },
        {
          path: 'agent',
          name: 'agent',
          component: () => import('@/views/agent/AgentView.vue'),
          meta: { title: '任务 Agent' },
        },
        {
          path: 'customer-service',
          name: 'customer-service',
          component: () => import('@/views/customer-service/CustomerServiceView.vue'),
          meta: { title: '智能客服' },
        },
        {
          path: 'data-analysis',
          name: 'data-analysis',
          component: () => import('@/views/data-analysis/DataAnalysisView.vue'),
          meta: { title: '数据分析' },
        },
        {
          path: 'chat-search',
          name: 'chat-search',
          component: () => import('@/views/chat/ChatSearchView.vue'),
          meta: { title: '全局搜索' },
        },
        {
          path: 'conversation-logs',
          name: 'conversation-logs',
          component: () => import('@/views/chat/ConversationLogView.vue'),
          meta: { title: '对话日志' },
        },
        {
          path: 'prompt-templates',
          name: 'prompt-templates',
          component: () => import('@/views/prompt/PromptTemplateView.vue'),
          meta: { title: 'Prompt 模板' },
        },
        {
          path: 'limits',
          name: 'token-limits',
          component: () => import('@/views/limit/TokenLimitView.vue'),
          meta: { title: '限额/限流配置' },
        },
        {
          path: 'model-routes',
          name: 'model-routes',
          component: () => import('@/views/model-route/ModelRouteView.vue'),
          meta: { title: '模型路由/降级' },
        },
        {
          path: 'moderation',
          name: 'moderation',
          component: () => import('@/views/moderation/ModerationView.vue'),
          meta: { title: '内容审查' },
        },
        {
          path: 'annotations',
          name: 'annotations',
          component: () => import('@/views/annotation/AnnotationView.vue'),
          meta: { title: '标注问答' },
        },
        {
          path: 'agent-eval',
          name: 'agent-eval',
          component: () => import('@/views/agent-eval/AgentEvalView.vue'),
          meta: { title: 'Agent 评测' },
        },
        {
          path: 'audit-logs',
          name: 'audit-logs',
          component: () => import('@/views/audit/AuditLogView.vue'),
          meta: { title: '操作审计日志' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/profile/ProfileView.vue'),
          meta: { title: '个人中心' },
        },
        // ============ 系统管理（RBAC） ============
        {
          path: 'system/users',
          name: 'system-users',
          component: () => import('@/views/system/UserManageView.vue'),
          meta: { title: '用户管理', perm: 'system:user:menu' },
        },
        {
          path: 'system/roles',
          name: 'system-roles',
          component: () => import('@/views/system/RoleManageView.vue'),
          meta: { title: '角色管理', perm: 'system:role:menu' },
        },
        {
          path: 'system/permissions',
          name: 'system-permissions',
          component: () => import('@/views/system/PermissionManageView.vue'),
          meta: { title: '权限管理', perm: 'system:permission:menu' },
        },
        {
          path: 'system/ops',
          name: 'system-ops',
          component: () => import('@/views/system/SystemOpsView.vue'),
          meta: { title: '系统运维' },
        },
        {
          path: 'system/api-keys',
          name: 'system-api-keys',
          component: () => import('@/views/system/ApiKeyView.vue'),
          meta: { title: 'API 密钥', perm: 'system:apikey:menu' },
        },
        {
          path: 'developer/api-docs',
          name: 'developer-api-docs',
          component: () => import('@/views/developer/ApiDocsView.vue'),
          meta: { title: 'API 调用示例' },
        },
      ],
    },
    // ============ 认证相关页面（不使用 DefaultLayout，独立全屏） ============
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { public: true, title: '注册' },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/auth/ForgotPasswordView.vue'),
      meta: { public: true, title: '忘记密码' },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/auth/ResetPasswordView.vue'),
      meta: { public: true, title: '重置密码' },
    },
    {
      path: '/auth/oauth/callback',
      name: 'oauth-callback',
      component: () => import('@/views/auth/OAuthCallback.vue'),
      meta: { public: true, title: '第三方登录' },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/error/ForbiddenView.vue'),
      meta: { title: '无权访问' },
    },
    {
      path: '/error',
      name: 'network-error',
      component: () => import('@/views/error/NetworkErrorView.vue'),
      meta: { title: '网络异常' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
})

export default router
