<script setup lang="ts">
/**
 * @description 平台公开落地页（首次访问的营销主页）
 *
 * 解决什么问题：未登录访客首次访问站点时，不应被路由守卫强制弹到登录页，
 * 而应先了解平台定位、核心能力、技术栈与开源仓库信息，再自主决定注册 / 登录。
 * 因此本页面为纯静态渲染（不依赖任何后端接口），路由 meta.public = true。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElIcon, ElTag } from 'element-plus'
import {
  ChatDotRound,
  Connection,
  Cpu,
  DataAnalysis,
  Grid,
  Lock,
  MagicStick,
  Monitor,
  Moon,
  Odometer,
  Share,
  Star,
  Sunny,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const authStore = useAuthStore()
const { isDark, toggleTheme } = useTheme()

/** GitHub 仓库地址（开源主仓库） */
const GITHUB_URL = 'https://github.com/jiangjiangbozan/ShangShu-AI'
/** Gitee 镜像仓库地址（国内访问加速） */
const GITEE_URL = 'https://gitee.com/jiang-bozan/ShangShu-AI'

/** 是否已登录：决定顶部主按钮是进入控制台还是引导注册 */
const isLoggedIn = computed(() => authStore.isAuthenticated)

/** 顶部导航锚点（与页面 section id 对应） */
const navItems = [
  { id: 'features', label: '核心能力' },
  { id: 'workflow', label: '工作流' },
  { id: 'stack', label: '技术栈' },
  { id: 'quickstart', label: '快速开始' },
  { id: 'opensource', label: '开源仓库' },
]

/** Hero 区统计数据 */
const stats = [
  { value: '20', label: '种工作流节点' },
  { value: '4', label: '类应用发布类型' },
  { value: '100%', label: 'Java 后端技术栈' },
  { value: 'Apache-2.0', label: '开源许可' },
]

/** 核心能力卡片 */
const features = [
  {
    icon: Cpu,
    title: '多模型统一接入',
    desc: 'DeepSeek、通义千问、Ollama 等供应商统一抽象为 Spring AI 模型配置，支持动态加载、连通性测试、路由与自动降级，无需重启即可切换。',
  },
  {
    icon: DataAnalysis,
    title: '企业知识库 RAG',
    desc: '完整检索链路：QueryRewrite → 向量 + 关键词双路召回 → RRF 融合 → Rerank 重排 → Parent Document 归并 → 引用溯源，命中结果 Redis 缓存。',
  },
  {
    icon: Share,
    title: '可视化工作流编排',
    desc: '20 种节点执行器，Dify 风格画布编辑器，支持条件分支、循环、变量上下文与节点级重试，执行过程通过 SSE 实时推送到前端 DAG 视图。',
  },
  {
    icon: MagicStick,
    title: 'Agent 执行引擎',
    desc: 'ReAct 与 Function Calling 双策略，工具注册与调用、轨迹逐步追踪，可结合知识检索与外部 HTTP 接口完成复杂任务。',
  },
  {
    icon: ChatDotRound,
    title: '智能客服与课程问答',
    desc: '意图识别、槽位填充、澄清模板与状态机对话管理，知识库兜底；课程问答场景支持多路检索与带引用来源的回答。',
  },
  {
    icon: Connection,
    title: '应用发布与 API Key',
    desc: 'chat / rag / agent / workflow 四类应用统一发布，签发 API Key 后以 OpenAI 兼容协议对外提供服务，可直接被第三方系统集成。',
  },
  {
    icon: Lock,
    title: '安全与权限治理',
    desc: 'JWT 双 Token + CSRF Double-Submit + XSS 过滤 + RBAC 权限点控制 + Jasypt 敏感配置加密，完整覆盖企业级安全基线。',
  },
  {
    icon: Odometer,
    title: '可观测与运营',
    desc: 'TraceId 全链路追踪、对话日志、LLM 调用统计、限额限流、内容审查、AI 应用评测与操作审计，Prometheus 指标 + Actuator 健康检查。',
  },
]

/** 工作流节点一览（与后端已注册的 NodeExecutor 一一对应） */
const workflowNodes = [
  'Start',
  'End',
  'LLM',
  'IfElse',
  'Loop',
  'Iteration',
  'KnowledgeRetrieval',
  'QuestionClassifier',
  'Agent',
  'Code',
  'HttpRequest',
  'Tool',
  'HumanInput',
  'VariableAssigner',
  'VariableAggregator',
  'TemplateTransform',
  'ParameterExtractor',
  'ListFilter',
  'DocExtractor',
  'Answer',
]

/** 技术栈分组 */
const stackGroups = [
  {
    icon: Cpu,
    title: '后端服务',
    items: [
      'Java 21',
      'Spring Boot 4.1',
      'Spring AI',
      'Spring Security 7',
      'MyBatis-Plus 3.5',
      'Flyway',
      'Redisson 4',
    ],
  },
  {
    icon: Grid,
    title: '中间件',
    items: ['MySQL 8', 'Redis 7 Stack（含 RediSearch）', 'Elasticsearch 9', 'RabbitMQ 3.13'],
  },
  {
    icon: Monitor,
    title: '前端与客户端',
    items: [
      'Vue 3',
      'TypeScript',
      'Vite',
      'Element Plus',
      'Pinia',
      'Vue Flow',
      'ECharts',
      'Kotlin / Compose',
      'uni-app 小程序',
    ],
  },
]

/** 快速开始三步 */
const quickSteps = [
  {
    step: '01',
    title: '启动中间件',
    desc: '一条命令拉起 MySQL、Redis Stack、Elasticsearch、RabbitMQ。',
    code: 'docker compose up -d',
  },
  {
    step: '02',
    title: '启动后端服务',
    desc: '开发默认值已与 Compose 对齐，无需额外配置文件。',
    code: 'cd api\nmvn spring-boot:run -Dspring-boot.run.profiles=dev',
  },
  {
    step: '03',
    title: '启动 Web 管理台',
    desc: '安装依赖并启动开发服务器，浏览器访问 5173 端口。',
    code: 'cd ai-agent-web\nnpm install\nnpm run dev',
  },
]

/** 主按钮行为：已登录进控制台，未登录引导注册 */
function onPrimaryCta() {
  router.push(isLoggedIn.value ? '/dashboard' : '/register')
}

/** 次按钮行为：跳转登录页 */
function onSecondaryCta() {
  router.push('/login')
}

/** 跳转登录并带上落地页来源，便于回跳 */
function onLoginWithSource() {
  router.push({ path: '/login', query: { redirect: '/dashboard' } })
}
</script>

<template>
  <div class="welcome-page">
    <!-- ============ 顶部导航 ============ -->
    <header class="site-header">
      <div class="site-header-inner">
        <div class="brand">
          <div class="brand-icon">
            <el-icon :size="20"><Cpu /></el-icon>
          </div>
          <div class="brand-text">
            <div class="brand-title">尚枢 AI</div>
            <div class="brand-subtitle">ShangShu AI</div>
          </div>
        </div>

        <nav class="nav-links">
          <a v-for="item in navItems" :key="item.id" class="nav-link" :href="`#${item.id}`">
            {{ item.label }}
          </a>
        </nav>

        <div class="header-actions">
          <el-button text circle :icon="isDark ? Sunny : Moon" @click="toggleTheme" />
          <el-button v-if="isLoggedIn" type="primary" @click="router.push('/dashboard')">
            进入控制台
          </el-button>
          <template v-else>
            <el-button text @click="onLoginWithSource">登录</el-button>
            <el-button type="primary" @click="onPrimaryCta">免费开始使用</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- ============ Hero ============ -->
    <section class="hero">
      <div class="hero-inner">
        <el-tag class="hero-badge" type="info" effect="plain" round>
          <el-icon><Star /></el-icon>
          Spring AI 原生 · 开源自托管 · Apache-2.0
        </el-tag>

        <h1 class="hero-title">尚枢 AI<span class="hero-title-en">ShangShu AI</span></h1>

        <p class="hero-subtitle">面向 Java / Spring Boot 团队的自托管 AI 应用平台</p>

        <p class="hero-desc">
          基于 Spring AI 构建，将多模型接入、企业知识库 RAG、可视化 Agent
          工作流、运行观测与安全治理整合到一套可部署、可扩展、可运营的系统中。 后端纯 Java
          技术栈，前后端同仓库，适合希望把 AI 能力真正纳入企业工程体系的团队。
        </p>

        <div class="hero-actions">
          <el-button type="primary" size="large" class="hero-btn" @click="onPrimaryCta">
            {{ isLoggedIn ? '进入控制台' : '免费开始使用' }}
          </el-button>
          <el-button size="large" class="hero-btn" @click="onSecondaryCta"
            >已有账号，登录</el-button
          >
          <el-button
            size="large"
            class="hero-btn"
            tag="a"
            :href="GITHUB_URL"
            target="_blank"
            rel="noopener noreferrer"
          >
            <el-icon><Connection /></el-icon>
            GitHub 仓库
          </el-button>
        </div>

        <div class="stats">
          <div v-for="s in stats" :key="s.label" class="stat-item">
            <div class="stat-value">{{ s.value }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 核心能力 ============ -->
    <section id="features" class="section">
      <div class="section-inner">
        <div class="section-head">
          <h2 class="section-title">核心能力</h2>
          <p class="section-desc">
            从模型接入到运行治理，一条链路打通 AI 应用的开发、发布与运营全过程。
          </p>
        </div>

        <div class="feature-grid">
          <article v-for="f in features" :key="f.title" class="feature-card">
            <div class="feature-icon">
              <el-icon :size="22"><component :is="f.icon" /></el-icon>
            </div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 工作流 ============ -->
    <section id="workflow" class="section section-alt">
      <div class="section-inner">
        <div class="section-head">
          <h2 class="section-title">可视化工作流引擎</h2>
          <p class="section-desc">
            20 种节点执行器覆盖从触发、推理、取数到输出的完整链路，前端拖拽即可编排，后端按 DAG
            拓扑执行并实时回传运行态。
          </p>
        </div>

        <div class="node-cloud">
          <el-tag v-for="node in workflowNodes" :key="node" class="node-tag" effect="plain">
            {{ node }}
          </el-tag>
        </div>

        <div class="workflow-points">
          <div class="workflow-point">
            <div class="workflow-point-title">拖拽式编排</div>
            <div class="workflow-point-desc">
              画布、节点配置面板、小地图与自定义连线，前端基于 Vue Flow 实现。
            </div>
          </div>
          <div class="workflow-point">
            <div class="workflow-point-title">可靠执行</div>
            <div class="workflow-point-desc">
              条件分支、循环、并行分支、节点级重试与执行记录持久化。
            </div>
          </div>
          <div class="workflow-point">
            <div class="workflow-point-title">实时可观测</div>
            <div class="workflow-point-desc">
              执行过程以 SSE 推送，节点状态着色 + 详情抽屉面板逐节点回溯。
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 技术栈 ============ -->
    <section id="stack" class="section">
      <div class="section-inner">
        <div class="section-head">
          <h2 class="section-title">技术栈</h2>
          <p class="section-desc">企业级基础设施与开箱即用的工程化配置，无第三方黑盒依赖。</p>
        </div>

        <div class="stack-grid">
          <div v-for="group in stackGroups" :key="group.title" class="stack-card">
            <div class="stack-head">
              <el-icon :size="20"><component :is="group.icon" /></el-icon>
              <span class="stack-title">{{ group.title }}</span>
            </div>
            <div class="stack-items">
              <span v-for="item in group.items" :key="item" class="stack-item">{{ item }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 快速开始 ============ -->
    <section id="quickstart" class="section section-alt">
      <div class="section-inner">
        <div class="section-head">
          <h2 class="section-title">快速开始</h2>
          <p class="section-desc">本地开发三步即可跑通，Docker Compose 亦可一键完成完整部署。</p>
        </div>

        <div class="steps">
          <div v-for="s in quickSteps" :key="s.step" class="step-card">
            <div class="step-number">{{ s.step }}</div>
            <h3 class="step-title">{{ s.title }}</h3>
            <p class="step-desc">{{ s.desc }}</p>
            <pre class="step-code"><code>{{ s.code }}</code></pre>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 开源仓库 ============ -->
    <section id="opensource" class="section">
      <div class="section-inner">
        <div class="section-head">
          <h2 class="section-title">完全开源</h2>
          <p class="section-desc">
            代码遵循 Apache License 2.0，欢迎提交 Issue、参与贡献或直接基于本项目搭建自己的 AI
            平台。
          </p>
        </div>

        <div class="repo-grid">
          <a class="repo-card" :href="GITHUB_URL" target="_blank" rel="noopener noreferrer">
            <div class="repo-name">GitHub</div>
            <div class="repo-desc">主仓库 · Issue / PR / Discussions</div>
            <div class="repo-link">{{ GITHUB_URL }}</div>
          </a>
          <a class="repo-card" :href="GITEE_URL" target="_blank" rel="noopener noreferrer">
            <div class="repo-name">Gitee</div>
            <div class="repo-desc">国内镜像仓库 · 自动同步</div>
            <div class="repo-link">{{ GITEE_URL }}</div>
          </a>
        </div>
      </div>
    </section>

    <!-- ============ 底部 CTA ============ -->
    <section class="cta">
      <div class="cta-inner">
        <h2 class="cta-title">现在开始搭建你自己的 AI 应用平台</h2>
        <p class="cta-desc">注册即可获得完整控制台权限：模型配置、知识库、工作流编排与应用发布。</p>
        <div class="cta-actions">
          <el-button type="primary" size="large" @click="onPrimaryCta">
            {{ isLoggedIn ? '进入控制台' : '免费注册' }}
          </el-button>
          <el-button
            size="large"
            tag="a"
            :href="GITHUB_URL"
            target="_blank"
            rel="noopener noreferrer"
          >
            查看源码
          </el-button>
        </div>
      </div>
    </section>

    <!-- ============ 页脚 ============ -->
    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-brand">尚枢 AI · ShangShu AI</div>
        <div class="footer-links">
          <a :href="GITHUB_URL" target="_blank" rel="noopener noreferrer">GitHub</a>
          <a :href="GITEE_URL" target="_blank" rel="noopener noreferrer">Gitee</a>
          <a :href="`${GITHUB_URL}/discussions`" target="_blank" rel="noopener noreferrer">
            社区讨论
          </a>
        </div>
        <div class="footer-copy">Apache License 2.0 · 自托管 AI 应用平台</div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.welcome-page {
  min-height: 100vh;
  background: var(--bg-page);
  color: var(--text-primary);
}

/* ============ 顶部导航 ============ */
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-default);
}

.site-header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  height: 64px;
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.brand-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgb(99 102 241 / 0.3);
}

.brand-title {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
  line-height: 1.2;
}

.brand-subtitle {
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.2;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  margin-left: auto;
}

.nav-link {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.nav-link:hover {
  color: var(--color-primary-600);
  text-decoration: none;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

/* ============ Hero ============ */
.hero {
  background:
    radial-gradient(1000px 400px at 15% -10%, rgb(99 102 241 / 0.16), transparent 60%),
    radial-gradient(800px 400px at 85% 0%, rgb(139 92 246 / 0.14), transparent 60%);
}

.hero-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-16) var(--space-6) var(--space-12);
  text-align: center;
}

.hero-badge {
  margin-bottom: var(--space-5);
}

.hero-title {
  font-size: clamp(36px, 6vw, 56px);
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.02em;
  margin-bottom: var(--space-3);
}

.hero-title-en {
  display: block;
  font-size: clamp(16px, 2.4vw, 24px);
  font-weight: var(--font-weight-medium);
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
  margin-top: var(--space-2);
}

.hero-subtitle {
  font-size: var(--font-size-xl);
  color: var(--text-secondary);
  margin-bottom: var(--space-4);
}

.hero-desc {
  max-width: 760px;
  margin: 0 auto var(--space-8);
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-3);
  margin-bottom: var(--space-10);
}

.hero-btn {
  min-width: 150px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
  max-width: 860px;
  margin: 0 auto;
}

.stat-item {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  padding: var(--space-4);
  box-shadow: var(--shadow-sm);
}

.stat-value {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin-top: var(--space-1);
}

/* ============ 通用 Section ============ */
.section {
  padding: var(--space-16) var(--space-6);
}

.section-alt {
  background: var(--bg-subtle);
}

.section-inner {
  max-width: 1200px;
  margin: 0 auto;
}

.section-head {
  text-align: center;
  margin-bottom: var(--space-10);
}

.section-title {
  font-size: clamp(24px, 3.4vw, 34px);
  margin-bottom: var(--space-3);
}

.section-desc {
  max-width: 720px;
  margin: 0 auto;
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
}

/* ============ 能力卡片 ============ */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--space-5);
}

.feature-card {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-2xl);
  padding: var(--space-6);
  transition:
    transform var(--transition-base),
    box-shadow var(--transition-base),
    border-color var(--transition-base);
}

.feature-card:hover {
  transform: translateY(-3px);
  border-color: var(--color-primary-300);
  box-shadow: var(--shadow-lg);
}

.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-primary-50), var(--color-primary-100));
  color: var(--color-primary-600);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-4);
}

.feature-title {
  font-size: var(--font-size-lg);
  margin-bottom: var(--space-2);
}

.feature-desc {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
}

/* ============ 工作流 ============ */
.node-cloud {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
  margin-bottom: var(--space-10);
}

.node-tag {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  border-radius: var(--radius-full);
  padding: 0 var(--space-3);
}

.workflow-points {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--space-5);
}

.workflow-point {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  padding: var(--space-5);
}

.workflow-point-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: var(--space-2);
}

.workflow-point-desc {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
}

/* ============ 技术栈 ============ */
.stack-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-5);
}

.stack-card {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-2xl);
  padding: var(--space-6);
}

.stack-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-primary-600);
  margin-bottom: var(--space-4);
}

.stack-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.stack-items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.stack-item {
  font-size: var(--font-size-xs);
  padding: 4px 10px;
  border-radius: var(--radius-md);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  border: 1px solid var(--border-default);
}

/* ============ 快速开始 ============ */
.steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-5);
}

.step-card {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-2xl);
  padding: var(--space-6);
}

.step-number {
  font-family: var(--font-mono);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-400);
  margin-bottom: var(--space-2);
}

.step-title {
  font-size: var(--font-size-lg);
  margin-bottom: var(--space-2);
}

.step-desc {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--space-4);
}

.step-code {
  margin: 0;
  padding: var(--space-4);
  background: var(--bg-subtle);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  line-height: 1.7;
  color: var(--text-primary);
  white-space: pre-wrap;
  overflow-x: auto;
}

/* ============ 开源仓库 ============ */
.repo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--space-5);
  max-width: 900px;
  margin: 0 auto;
}

.repo-card {
  display: block;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-2xl);
  padding: var(--space-6);
  color: inherit;
  transition:
    transform var(--transition-base),
    border-color var(--transition-base),
    box-shadow var(--transition-base);
}

.repo-card:hover {
  transform: translateY(-3px);
  border-color: var(--color-primary-400);
  box-shadow: var(--shadow-lg);
  text-decoration: none;
}

.repo-name {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-600);
  margin-bottom: var(--space-2);
}

.repo-desc {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--space-3);
}

.repo-link {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  word-break: break-all;
}

/* ============ 底部 CTA ============ */
.cta {
  padding: var(--space-16) var(--space-6);
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
}

.cta-inner {
  max-width: 760px;
  margin: 0 auto;
  text-align: center;
}

.cta-title {
  color: #fff;
  font-size: clamp(22px, 3.2vw, 32px);
  margin-bottom: var(--space-3);
}

.cta-desc {
  color: rgb(255 255 255 / 0.85);
  margin-bottom: var(--space-6);
}

.cta-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

/* ============ 页脚 ============ */
.site-footer {
  background: var(--bg-card);
  border-top: 1px solid var(--border-default);
  padding: var(--space-8) var(--space-6);
}

.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.footer-brand {
  font-weight: var(--font-weight-semibold);
}

.footer-links {
  display: flex;
  gap: var(--space-5);
  font-size: var(--font-size-sm);
}

.footer-copy {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
  .nav-links {
    display: none;
  }
  .header-actions {
    margin-left: auto;
  }
}

@media (max-width: 640px) {
  .site-header-inner {
    padding: 0 var(--space-4);
  }
  .section {
    padding: var(--space-10) var(--space-4);
  }
  .hero-inner {
    padding: var(--space-10) var(--space-4) var(--space-8);
  }
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hero-btn {
    flex: 1 1 100%;
  }
  .footer-inner {
    flex-direction: column;
    text-align: center;
  }
}
</style>
