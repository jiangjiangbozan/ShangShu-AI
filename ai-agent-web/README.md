# ai-agent-web · 尚枢 AI Web 管理台

尚枢 AI（ShangShu AI）的 Web 管理台 —— 基于 Vue 3 + Element Plus + Vue Flow 的 AI 应用平台前端，覆盖 AI 应用、应用开发、模型与知识、运行与治理、系统管理五大业务域。

## 技术栈

- Vue 3（`<script setup lang="ts">`）+ Vite + TypeScript
- Element Plus（UI 组件库）+ Pinia（状态管理）+ Vue Router
- Vue Flow（可视化工作流编辑器）+ ECharts（图表）
- Axios（统一封装，自动解包后端 `Result<T>`）

完整依赖以 [package.json](package.json) 为准。

## 功能模块

| 域 | 页面 |
| --- | --- |
| **AI 应用** | 对话聊天 · 任务 Agent · 课程问答 · 智能客服 |
| **应用开发** | 工作流列表 · 工作流编辑器（Vue Flow DAG）· 执行记录 · 执行详情回放 · Prompt 模板（版本管理） |
| **模型与知识** | 模型配置 · 模型路由/降级 · 知识库 RAG · 检索测试 · 反馈日志 |
| **运行与治理** | 对话日志 · 全局搜索 · 限额/限流 · 内容审查 · 标注问答 · Agent 评测 · 操作审计 |
| **系统管理** | 用户 / 角色 / 权限管理（RBAC）· 个人中心 |

菜单为动态加载：后端 `/api/v1/menus` 返回当前用户权限内的菜单树，前端按权限渲染。

## 开发命令

```bash
npm install        # 安装依赖（Node 22+）
npm run dev        # 开发服务器 http://localhost:5173（代理 API 到 localhost:8080）
npm run build      # 类型检查 + 生产构建
npm run type-check # 仅类型检查
```

后端启动方式见 [api/README.md](../api/README.md)；本地开发推荐先用仓库根目录 `docker compose up -d` 起中间件。

## 目录结构

```
src/
├── api/                  # 按业务域组织的请求函数与类型
├── stores/               # Pinia store（数据获取 + 状态 + 业务方法）
├── views/                # 页面组件（按业务域分目录）
│   ├── workflow/         # 工作流：列表 / 编辑器 / 执行记录 / 节点组件
│   ├── rag/              # 知识库：文档管理 / 检索测试 / 反馈日志
│   └── ...
├── layouts/              # DefaultLayout（侧边菜单 + 顶栏）
├── router/               # 路由表（按业务域分组）
└── styles/               # 全局变量与工具类
```

## 开发约定

遵循 [AGENTS.md](AGENTS.md)：Element Plus 组件优先、列表/表单统一结构、Service 层承担数据获取与状态、事件方法 `onXxx` 命名、工作流节点开发流程等。

## 相关文档

- 架构说明：[docs/platform/architecture.md](../docs/platform/architecture.md)
- 工作流节点扩展：[docs/workflow-node-spi.md](../docs/workflow-node-spi.md)
- 参与贡献：[CONTRIBUTING.md](../CONTRIBUTING.md)
