# Good First Issues · 新人贡献任务清单

欢迎参与尚枢 AI 开发！以下 5 个任务难度友好、范围可控，适合第一次贡献。开始前请阅读 [CONTRIBUTING.md](../CONTRIBUTING.md) 了解开发环境与 PR 流程。认领方式：在对应 Issue 下评论 "认领"，或直接提 PR 并在描述中关联任务编号。

---

## GFI-1 检索测试页面 UI 优化

- **难度**：⭐ 入门（纯前端）
- **背景**：知识库模块已内置检索测试功能（原始问题 → QueryRewrite → 向量召回 → 关键词召回 → RRF 融合 → Rerank 逐步展开）。当前实现以基础列表展示为主，链路步骤的层次感和可读性还有提升空间。
- **涉及文件**：
  - `ai-agent-web/src/views/KnowledgeBaseView.vue`（检索测试区域）
  - 相关 API 封装：`ai-agent-web/src/api/knowledge.ts`
- **验收标准**：
  1. 检索链路各步骤用步骤条 / 折叠面板分区展示，当前步骤可展开查看详情
  2. 每路召回结果展示分数、文档来源等关键信息，分数可视化（进度条或标签）
  3. `npm run build` 与 `vue-tsc` 类型检查通过
  4. 不改动后端接口与数据结构
- **参考**：页面整体遵循 `ai-agent-web/AGENTS.md` 约定与 Element Plus 组件规范

---

## GFI-2 工作流 20 种节点使用文档

- **难度**：⭐ 入门（纯文档）
- **背景**：工作流引擎已实现 20 种节点执行器（LLM、IfElse、Loop、Iteration、KnowledgeRetrieval、QuestionClassifier、Agent、Code、HttpRequest、Tool、HumanInput、VariableAssigner、VariableAggregator、TemplateTransform、ParameterExtractor、ListFilter、DocExtractor、Answer、Start、End），但缺少面向使用者的节点配置文档。目前仅有面向扩展开发者的 SPI 文档（`docs/workflow-node-spi.md`）。
- **涉及文件**：
  - 新建 `docs/workflow-nodes.md`
  - 阅读源码：`api/src/main/java/com/ai/api/workflow/engine/node/` 下 20 个 `*NodeExecutor.java`
- **验收标准**：
  1. 每种节点一个小节：用途说明、输入输出（变量引用表达式）、关键配置项、一个最小配置示例
  2. 说明节点间变量传递方式（`${var.path}` 与 `${global.key}` 作用域）
  3. 配置项名称与源码实际读取的字段一致（以 `node.data` 读取处为准）
  4. 中文成稿，风格与 `docs/workflow-node-spi.md` 一致
- **提示**：可与 GFI-4（示例模板库）配套提交，文档中的示例可直接用模板 JSON

---

## GFI-3 Web 管理台深色模式

- **难度**：⭐⭐ 进阶（前端全局样式）
- **背景**：管理台目前只有亮色主题。Element Plus 内置暗色 CSS 变量方案（`html.dark`），业务代码中少量硬编码颜色需要一并梳理。
- **涉及文件**：
  - `ai-agent-web/src/App.vue`、全局样式文件（`src/assets` / `src/styles` 下）
  - `ai-agent-web/src/layouts/DefaultLayout.vue`（主题切换入口）
  - 工作流画布组件（`ai-agent-web/src/views/workflow/` 下 Vue Flow 相关节点）颜色适配
- **验收标准**：
  1. 顶栏提供亮 / 暗 / 跟随系统三档切换，选择持久化到 localStorage
  2. 主体页面（登录、仪表盘、表格页、工作流画布）在暗色下无文字与背景对比度问题
  3. 使用 Element Plus 官方暗色变量，不引入新的主题依赖
  4. `npm run build` 通过，无明显视觉回归（附切换前后截图）
- **提示**：优先梳理全局变量，避免逐组件硬编码覆盖

---

## GFI-4 示例工作流模板库

- **难度**：⭐⭐ 进阶（后端 DSL + 文档）
- **背景**：工作流 DSL（graph JSON：nodes / edges / 变量 / 重试配置）功能完整，但新用户缺少可直接导入的示例。需要建立一套官方示例模板库。
- **涉及文件**：
  - 新建 `dev/workflow-templates/` 目录，每个模板一个 JSON 文件
  - DSL 结构参考：`api/src/main/java/com/ai/api/workflow/engine/WorkflowEngine.java` 与节点执行器读取的 `node.data` 字段
- **验收标准**：
  1. 至少 4 个模板：① 智能问答（LLM + Answer）② 知识库问答（KnowledgeRetrieval + LLM）③ 意图分流（QuestionClassifier + 分支）④ 带重试的批量处理（Loop/Iteration + retry 配置 + onError=continue）
  2. 每个模板附 README 说明：用途、节点清单、需要的环境（模型 / 知识库）
  3. 模板 JSON 通过 DAG 拓扑校验（可用单节点调试或导入功能验证）
  4. 变量引用使用 `${global.*}` / `${var.*}` 正确语法
- **提示**：`docs/workflow-nodes.md`（GFI-2）落地后可交叉引用

---

## GFI-5 英文版 README

- **难度**：⭐ 入门（文档 + 翻译）
- **背景**：项目定位面向 Java / Spring Boot 团队，Spring AI 生态的英文社区更大。需要与中文 README 结构一致的英文版，并在两个版本间互相链接。
- **涉及文件**：
  - 新建 `README_EN.md`
  - `README.md`（顶部加语言切换链接 `[English](README_EN.md)`）
- **验收标准**：
  1. 结构与中文 README 一致：定位一句话、功能特性表、技术栈、仓库结构、快速开始（两种模式）、Roadmap
  2. 术语统一：AI Application / Workflow Engine / Knowledge Base RAG / Node Executor 等，与 Spring AI 官方文档术语对齐
  3. 代码块与链接路径保持原样可用
  4. 两个 README 顶部均有语言切换入口
- **提示**：翻译过程中发现中文版描述与代码不一致的地方，请单独提 Issue 反馈

---

## 认领与进度

| 编号  | 任务                     | 难度 | 状态                                                                                                          |
| ----- | ------------------------ | ---- | ------------------------------------------------------------------------------------------------------------- |
| GFI-1 | 检索测试页面 UI 优化     | ⭐   | 待认领（[#1](https://github.com/jiangjiangbozan/ShangShu-AI/issues/1)）                                       |
| GFI-2 | 工作流 20 种节点使用文档 | ⭐   | ✅ 已完成 → [docs/workflow-nodes.md](workflow-nodes.md)                                                       |
| GFI-3 | Web 管理台深色模式       | ⭐⭐ | 待认领（[#2](https://github.com/jiangjiangbozan/ShangShu-AI/issues/2)）                                       |
| GFI-4 | 示例工作流模板库         | ⭐⭐ | ✅ 已完成 → [docs/workflow-templates/](workflow-templates/)（对话/知识问答/意图分流/批量任务 4 个可导入模板） |
| GFI-5 | 英文版 README            | ⭐   | ✅ 已完成 → [README_EN.md](../README_EN.md)                                                                   |

完成 PR 合并后此表会同步更新。遇到问题欢迎到 [Discussions](https://github.com/jiangjiangbozan/ShangShu-AI/discussions) 提问。
