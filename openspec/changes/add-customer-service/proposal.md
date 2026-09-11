## Why

平台已完成 M4 Agent Runtime，但所有业务场景仍走通用 Chat 链路，缺少任务型多轮对话能力。`docs/development-plan.md` M5 验收指标要求"客服多轮对话通过率 ≥ 80%、意图识别准确率 ≥ 90%"，本 change 是该里程碑的客服侧交付物。

当前痛点：

1. **意图识别缺失**：用户咨询"查销量"或"产品对比"全部走 LLM 自由生成，无法路由到具体业务 Skill
2. **槽位无管理**：业务参数（地区 / 产品 / 时间范围）零散放在用户 prompt 中，多轮澄清依赖人工
3. **无知识库回退**：FAQ 命中后仍调用 LLM，成本高且幻觉风险
4. **无客服工作台**：运营无法查看会话状态、人工接管、回流标注

本 change 解决：

- 意图识别 + 槽位填充 Skill 化，复用 M4 SkillRegistry
- 多轮澄清状态机，跨会话持久化到 `cs_dialog_state` 表
- 知识库匹配回退（命中即返回，不调用 LLM）
- 客服工作台：会话列表 + 接管 + 标注回流

## What Changes

### 1. 意图识别 Skill

- `intent_classifier` Skill（type=local，LLM 实现）：输入 query，输出 `{intent, confidence, slots_needed}`
- 意图注册表 `cs_intent`：name / description / slots_schema / fallback_intent
- 阈值可配：confidence < 0.6 触发澄清或回退到 `fallback_intent`

### 2. 槽位填充与多轮澄清

- `slot_filler` Skill（type=local）：根据 `slots_schema` + 历史对话，输出缺失槽位列表
- 状态机：`collecting → confirming → executing → done`
- 槽位持久化到 `cs_dialog_state`（runtimeId + intent + slots JSON）
- 澄清话术模板：`cs_clarify_template`（按 slot_type 渲染）

### 3. 知识库匹配回退

- 复用 M3 `RagRetrievalService.search`：先于 LLM 调用，top1 score > 0.85 直接返回
- 命中记录写 `cs_kb_hit_log`（hit_query / kb_id / doc_id / score）
- 未命中则走 LLM 生成 + 业务 Skill 执行

### 4. 客服工作台

- 会话列表 `GET /api/v1/cs/sessions`：按 status / assignedTo / createdAt 过滤
- 人工接管 `POST /api/v1/cs/sessions/{id}/takeover`：转人工，停掉 ReactEngine 循环
- 标注回流 `POST /api/v1/cs/sessions/{id}/label`：正负样本写入 `cs_label` 表，反馈训练

### 5. 前端客服工作台

- `CustomerServiceView.vue`：左侧会话列表 + 右侧对话窗口 + 顶部标签栏
- 接管按钮 / 转人工按钮 / 标注按钮（正/负/中性）
- 会话搜索：按用户 / 状态 / 时间范围

## Capabilities

### New Capabilities

- `cs/intent`: 意图识别 Skill + 意图注册表 + 置信度阈值路由
- `cs/slot`: 槽位填充 Skill + 多轮澄清状态机 + 跨会话持久化
- `cs/kb-fallback`: 知识库匹配回退 + 命中日志
- `cs/workbench`: 客服工作台 + 接管 + 标注回流

### Modified Capabilities

无。本 change 复用 M4 `agent/runtime` 与 M3 `rag/retrieval`，不修改其外部接口。`ChatStreamService` 不变，客服场景走新的 `CustomerServiceOrchestrator` 入口。

## Impact

- **数据库**：4 张表迁移脚本（`cs_intent` / `cs_dialog_state` / `cs_kb_hit_log` / `cs_label`）
- **后端代码**：新增 `api/src/main/java/com/ai/api/cs/` 包，子模块 `intent` / `slot` / `kb` / `workbench` / `controller` / `mapper` / `entity`
- **前端代码**：新增 `ai-agent-web/src/views/cs/CustomerServiceView.vue` 与 `ai-agent-web/src/api/cs.ts`
- **配置**：`application-dev.yml` 新增 `cs.*` 配置块（kb-hit-threshold / intent-confidence-threshold / clarify-max-rounds）
- **依赖**：无新引入 Maven 依赖
- **关联 change**：
  - 依赖 `add-agent-runtime`（Runtime + Skill 体系）
  - 依赖 `add-p3-rag-feedback-ops`（RagRetrievalService）
  - 阻塞 M5 `add-multi-agent-orchestration`（客服 Agent 作为多 Agent 协作的入口）

## 非目标

- **不做** 多 Agent 协作（M5 `add-multi-agent-orchestration` 范围）
- **不做** 完整工单系统（仅会话级标注，工单流转留业务系统）
- **不做** 语音客服（仅文本 SSE）
- **不做** 客服培训 / 考核（仅数据回流标注）
- **不做** 实时质检（M6 评测体系范围）
- **不做** 多渠道接入（微信 / 钉钉 / 电话，V2 范围）

## 验收标准

1. `intent_classifier` Skill 对 5 类业务意图（销量查询 / 产品对比 / 报销标准 / 故障排查 / 闲聊）准确率 ≥ 90%
2. 多轮澄清场景：用户问"查销量"系统追问"哪个地区？"，用户回答后槽位 `region` 填充成功
3. 知识库命中 top1 score > 0.85 时跳过 LLM，命中率统计接口 `GET /api/v1/cs/stats/kb-hit` 返回近 7 日命中率
4. `POST /api/v1/cs/sessions/{id}/takeover` 转人工后 ReactEngine 循环停止，会话状态转 `human`
5. `POST /api/v1/cs/sessions/{id}/label` 写入 `cs_label`，回流数据集用于意图识别模型微调
6. 前端客服工作台可查看会话列表 / 接管 / 标注，三个操作均落库
7. `cs_dialog_state` 表持久化槽位 + 意图，重启后状态可恢复
8. 单测覆盖：IntentClassifierSkill / SlotFillerSkill / DialogStateMachine / KbFallbackService
