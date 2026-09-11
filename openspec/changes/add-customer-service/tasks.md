# 实施任务 - add-customer-service

> 任务粒度 ≤ 4 小时；按数据层 → 实体/Mapper → 能力层 → 业务层 → 接入层顺序执行；每个任务标注验证方式。
>
> 状态：已完成

## 阶段 1: 数据层与配置

### Task 1.1: 数据库迁移脚本

- [x] 新增 `api/src/main/resources/db/migration/V20260902__add_customer_service_tables.sql`
- [x] 创建 5 张表：`cs_intent` / `cs_dialog_state` / `cs_kb_hit_log` / `cs_label` / `cs_session` / `cs_clarify_template`
- [x] 索引：`idx_cs_session_status` / `idx_cs_dialog_runtime` / `idx_cs_kb_hit_runtime` / `uk_cs_clarify_intent_slot`
- **验证**：应用启动 Flyway 自动迁移；`SHOW TABLES LIKE 'cs_%'` 返回 6 行

### Task 1.2: CustomerServiceProperties 配置

- [x] 新增 `com.ai.api.cs.config.CustomerServiceProperties`（`@ConfigurationProperties(prefix="cs")`）
- [x] 字段：`intent-confidence-threshold=0.6` / `kb-hit-threshold=0.85` / `clarify-max-rounds=3` / `fallback-intent=small_talk`
- [x] `application-dev.yml` 新增 `cs:` 配置块
- **验证**：`mvn compile` 通过；`@SpringBootTest` 注入 `CustomerServiceProperties` 字段非 null

## 阶段 2: 实体与 Mapper 层

### Task 2.1: 6 个实体类

- [x] 新增 `com.ai.api.cs.entity.CsIntent` / `CsDialogState` / `CsKbHitLog` / `CsLabel` / `CsSession` / `CsClarifyTemplate`
- [x] JSON 字段（`slots_schema` / `slots`）使用 `@TableField(typeHandler = JacksonTypeHandler.class)`
- **验证**：`mvn compile` 通过

### Task 2.2: 6 个 Mapper

- [x] 新增 `CsIntentMapper` / `CsDialogStateMapper` / `CsKbHitLogMapper` / `CsLabelMapper` / `CsSessionMapper` / `CsClarifyTemplateMapper`
- [x] `CsDialogStateMapper` 自定义：`selectByRuntimeId(Long)` / `selectBySessionId(Long)`
- [x] `CsSessionMapper` 自定义：`selectByStatus(String)` / `selectByAssignedTo(Long)`
- **验证**：`@MybatisTest` 注入 Mapper，`SELECT 1` 成功

## 阶段 3: 意图识别（模块 1）

### Task 3.1: IntentClassifierSkill

- [x] 新增 `com.ai.api.cs.intent.IntentClassifierSkill`（`@Skill(name="intent_classifier", type=SkillType.LOCAL)`）
- [x] 输入 `{"query":<str>,"session_id":<long>}`
- [x] 关键词规则优先：5 类意图关键词命中即返回 `confidence=1.0`
- [x] 未命中调 LLM，输出 JSON `{"intent":<str>,"confidence":<double>,"slots_needed":[<str>]}`
- [x] LLM 输出解析失败降级到 `fallback_intent`
- **验证**：单测覆盖关键词命中 / LLM 命中 / 解析失败兜底

### Task 3.2: IntentRegistry 启动初始化

- [x] 新增 `com.ai.api.cs.intent.IntentRegistry implements ApplicationRunner`
- [x] 启动时检查 `cs_intent` 表，5 类内置意图不存在则插入
- [x] 5 类意图：`sales_query` / `product_compare` / `reimbursement_standard` / `fault_diagnosis` / `small_talk`
- [x] 日志输出 `[IntentRegistry][init intents: 5]`
- **验证**：应用启动后 `SELECT COUNT(*) FROM cs_intent` 返回 5

### Task 3.3: 意图识别单元测试

- [x] 新增 `IntentClassifierSkillTest`
- [x] 覆盖：高置信度（关键词命中）/ 低置信度触发澄清 / fallback 兜底
- **验证**：`mvn test` 通过

## 阶段 4: 槽位填充与澄清（模块 2）

### Task 4.1: SlotFillerSkill

- [x] 新增 `com.ai.api.cs.slot.SlotFillerSkill`（`@Skill(name="slot_filler", type=SkillType.LOCAL)`）
- [x] 输入 `{"query":<str>,"intent":<str>,"slots_schema":<json>,"history":[<msg>]}`
- [x] 调 LLM 输出 JSON `{"filled_slots":<map>,"missing_slots":[<str>],"is_complete":<bool>}`
- [x] 解析失败重试 1 次
- **验证**：单测覆盖完整填充 / 部分填充 / 空填充

### Task 4.2: DialogStateMachine

- [x] 新增 `com.ai.api.cs.slot.DialogStateMachine`
- [x] 4 状态：`COLLECTING / CONFIRMING / EXECUTING / DONE`
- [x] `next(state, slotResult)` 计算下一状态
  - `COLLECTING + is_complete=true` → `CONFIRMING`
  - `COLLECTING + is_complete=false` → `COLLECTING`（clarify_rounds++）
  - `CONFIRMING + user_confirm=true` → `EXECUTING`
  - `EXECUTING + skill_done=true` → `DONE`
- [x] `clarify_rounds > max_rounds` 强制 `fallback_intent`
- **验证**：单测覆盖所有合法转移 + 兜底

### Task 4.3: ClarifyTemplateRenderer

- [x] 新增 `com.ai.api.cs.slot.ClarifyTemplateRenderer`
- [x] `render(intent, missingSlots)` 查 `cs_clarify_template` 拼接话术
- [x] 多槽位合并："请告诉我您想查询的地区和产品"
- [x] 未配置模板用默认："请提供更多信息"
- **验证**：单测覆盖单槽位 / 多槽位合并 / 默认模板

### Task 4.4: 槽位填充单元测试

- [x] 新增 `SlotFillerSkillTest` / `DialogStateMachineTest` / `ClarifyTemplateRendererTest`
- **验证**：`mvn test` 通过

## 阶段 5: KB 匹配回退（模块 3）

### Task 5.1: KbFallbackService

- [x] 新增 `com.ai.api.cs.kb.KbFallbackService`
- [x] `tryFallback(runtimeId, query)`：调 `RagRetrievalService.search(query, topK=1)`
- [x] top1 score > `cs.kb-hit-threshold` 返回命中片段，否则返回 null
- [x] 命中调用 `KbHitLogRecorder.record(hit=1)`，未命中 `record(hit=0)`
- **验证**：单测 mock `RagRetrievalService`，覆盖命中 / 未命中

### Task 5.2: KbHitLogRecorder

- [x] 新增 `com.ai.api.cs.kb.KbHitLogRecorder`
- [x] `record(runtimeId, query, kbId, docId, score, hit)` 异步写 `cs_kb_hit_log`
- [x] `@Async` 避免阻塞主流程
- **验证**：单测验证 Mapper 调用

### Task 5.3: KB 命中统计接口

- [x] `CsKbHitLogMapper` 新增 `selectStatsByDays(int days)` 返回按天聚合
- [x] 新增 `GET /api/v1/cs/stats/kb-hit?days=7` 接口
- **验证**：curl 调用返回 `{"total":..,"hit":..,"hit_rate":..}`

## 阶段 6: 客服工作台（模块 4）

### Task 6.1: SessionService

- [x] 新增 `com.ai.api.cs.workbench.SessionService`
- [x] `listSessions(status, assignedTo, page, size)` 分页查询 `cs_session` JOIN `chat_session`
- [x] `takeover(sessionId, userId)`：
  1. runtime 调 `AgentRuntimeService.pause(runtimeId)`
  2. `cs_session.status=human, assigned_to=userId`
- [x] `getById(sessionId)` 返回详情
- **验证**：单测 mock Mapper + RuntimeService

### Task 6.2: LabelService

- [x] 新增 `com.ai.api.cs.workbench.LabelService`
- [x] `label(sessionId, labelType, content, labelerId)` 写 `cs_label`
- [x] `listBySession(sessionId)` 查询会话所有标注
- **验证**：单测验证 Mapper 调用

### Task 6.3: Controller 层

- [x] 新增 `SessionController`
  - `GET /api/v1/cs/sessions` 分页列表
  - `GET /api/v1/cs/sessions/{id}` 详情
  - `POST /api/v1/cs/sessions/{id}/takeover` 接管
- [x] 新增 `LabelController`
  - `POST /api/v1/cs/sessions/{id}/label` 标注
  - `GET /api/v1/cs/sessions/{id}/labels` 列表
- [x] 新增 `CustomerServiceController` 入口
  - `POST /api/v1/cs/chat` 处理用户消息（编排 intent → slot → kb-fallback → react-engine）
- **验证**：curl 调用各接口返回 `{"code":0}`

## 阶段 7: 前端客服工作台

### Task 7.1: API + 路由

- [x] 新增 `ai-agent-web/src/api/cs.ts`（封装会话 / 接管 / 标注 / 统计接口）
- [x] 新增路由 `/customer-service` 启用菜单
- **验证**：浏览器访问 `/customer-service` 页面正常渲染

### Task 7.2: CustomerServiceView

- [x] 新增 `ai-agent-web/src/views/cs/CustomerServiceView.vue`
- [x] 左侧会话列表（状态徽章 + 搜索）
- [x] 右侧对话窗口（复用 ChatView 的消息渲染）
- [x] 顶部操作栏：接管 / 转人工 / 标注按钮
- [x] 标注弹窗：正/负/中性 + 内容输入
- **验证**：手动测试三个操作均落库

### Task 7.3: 启用菜单

- [x] `DefaultLayout.vue` 中 `/customer-service` 菜单 `disabled=false`，移除 `badge: 'soon'`
- **验证**：左侧菜单可点击

## 阶段 8: 测试与验收

### Task 8.1: 单元测试补全

- [x] `IntentClassifierSkillTest` / `SlotFillerSkillTest` / `DialogStateMachineTest`
- [x] `KbFallbackServiceTest` / `SessionServiceTest` / `LabelServiceTest`
- **验证**：`mvn test` 全部通过

### Task 8.2: 集成测试

- [x] 新增 `CustomerServiceIntegrationTest` `@SpringBootTest`
- [x] 覆盖：用户 query → 意图识别 → 槽位填充 → KB 回退 → LLM 回答
- [x] 覆盖：接管后 ReactEngine 停止
- **验证**：测试全部通过

### Task 8.3: 验收脚本

- [x] 新增 `docs/dev-log/2026-08-23.md` 记录 curl 脚本
- [x] 覆盖 8 条验收标准（proposal.md 列出）
- **验证**：手动执行 curl 全部返回 `{"code":0}`
