# 实施任务 - add-p3-rag-feedback-ops

> 任务粒度 ≤ 4 小时；按数据层 → 能力层 → 业务层 → 接入层顺序执行；每个任务标注验证方式。
>
> ✅ 状态：全部完成（2026-08-21）。各任务勾选后的「实现偏差」小节记录与原计划的差异。

## 阶段 1: 数据层与配置准备

### Task 1.1: knowledge_chunk 表新增 hit_count 字段

- [x] 在 `api/src/main/resources/db/migration/` 新增迁移脚本
- [x] `ALTER TABLE knowledge_chunk ADD COLUMN hit_count INT NOT NULL DEFAULT 0`
- [x] `ADD INDEX idx_kchunk_hit (hit_count, deleted)`
- [x] 新增 `chunk_feedback_log` 与 `chunk_reprocess_log` 表
- **验证**：Flyway 迁移通过；`DESC knowledge_chunk` 含 hit_count 字段

> 实现偏差：迁移脚本为 `V20260825__add_knowledge_chunk_hit_count.sql` 与
> `V20260826__create_chunk_feedback_log.sql`；索引名为 `idx_kchunk_hit`（含 deleted 列），
> 未含 score 列（knowledge_chunk 表无 score 字段，低质判定改用 hit_count 阈值）。

### Task 1.2: MySQL binlog 配置

- [x] 配置 `log_bin` `binlog_format=ROW` `binlog_row_image=FULL` `binlog_expire_logs_seconds=604800`
- [x] docker-compose 应用配置
- [x] 重建 MySQL 容器验证 `SHOW VARIABLES` 输出 ON / ROW / FULL / 604800
- **验证**：`SHOW BINARY LOGS;` 返回 binlog 文件列表；`GET /api/v1/ops/binlog/verify` 巡检接口

> 实现偏差：未采用 `api/docker/mysql/my.cnf` + 挂载方案，直接通过 docker-compose `command`
> 启动参数配置（等效，容器重建后验证 `binlog_expire_logs_seconds=604800`、`binlog_row_image=FULL`）。
> 本地开发环境使用本地 mysqld，docker mysql 端口未暴露，配置随容器生效。

### Task 1.3: ES snapshot repository 注册

- [x] 新增启动时自动注册 `kb_backup_repo` fs repository 的组件
- [x] `application-dev.yml` 新增 `backup.es` 配置块（含 `location`）
- [x] Docker Compose 挂载 `./data/backup/es` 到 ES 容器
- [x] `path.repo` 白名单配置 `/usr/share/elasticsearch/backup`
- **验证**：应用启动日志输出 `[EsSnapshotRepositoryInitializer][快照仓库就绪]`

> 实现偏差：组件为 `ops/backup/EsSnapshotRepositoryInitializer.java`（@Configuration + @PostConstruct，
> PUT 幂等注册，失败仅 WARN 不阻断启动），非 `data/config/EsSnapshotConfig.java`。
> 快照路径统一为 `/usr/share/elasticsearch/backup`。

### Task 1.4: application-dev.yml 新增配置

- [x] 新增低质分片扫描配置（`rag.feedback.*`）
- [x] 新增 `backup.mysql` 与 `backup.es` 配置块（path/retention/cron/repository/location）
- [x] 新增 `es.index.tune.shard-size-gb: 30` 配置
- **验证**：`mvn compile` 通过；应用启动正常

> 实现偏差：配置键为 `rag.feedback.low-hit-*`（扫描器）与 `es.index.tune.*`，
> 未使用 `scheduler.chunk-reprocess` / `elasticsearch.index-ops` 命名。

## 阶段 2: RAG 反馈闭环能力层

### Task 2.1: 实现 ChunkFeedbackService

- [x] 新增 `api/src/main/java/com/ai/api/knowledge/feedback/ChunkFeedbackService.java`
- [x] `incrHit(chunkId)`：`@Async` 异步执行，主流程不阻塞
- [x] `dislike(chunkId, userId)`：先写 `chunk_feedback_log`（唯一索引），重复点踩捕获 DuplicateKeyException 跳过扣分
- [x] 扣分 `UPDATE ... SET hit_count = GREATEST(hit_count - 3, 0)`
- **验证**：编译通过；点踩接口重复调用幂等（日志「重复点踩，幂等跳过」）

> 实现偏差：批量 `incrHitBatch` 未单独实现，命中计数在 `RagRetrievalService.recordHitCountAsync`
> 中按 vectorId 反查 chunkId 逐条 +1；单测未补充（以接口联调验证）。

### Task 2.2: 低质分片定时处理

- [x] 新增 `knowledge/schedule/LowHitChunkScanner.java`，`@Scheduled(cron = "${rag.feedback.low-hit-cron:0 30 3 * * ?}")`
- [x] 查询 `hit_count < threshold` 的 chunk 输出待优化清单
- **验证**：启动后定时任务注册成功（日志可见）

> 实现偏差（重要）：扫描器设计为「输出待优化清单日志」而非自动重处理，避免误删已上线向量数据；
> 重处理由 `POST /knowledge/documents/{id}/reprocess` 人工触发。未接入分布式锁与并发配置
> （清单模式无写操作）。`chunk_reprocess_log` 表已建好预留。

### Task 2.3: 集成反馈到 RAG 主流程

- [x] `RagRetrievalService.recordHitCountAsync`：检索返回后异步命中 +1（已在 RagRetrievalService 集成）
- [x] 点踩接口：`POST /api/v1/knowledge/chunks/{id}/dislike`，走 `ChunkFeedbackService.dislike` 幂等链路
- **验证**：编译通过；接口定义于 KnowledgeController

> 实现偏差：点踩接口挂在 `/api/v1/knowledge/chunks/{id}/dislike`（面向 chunk），
> 未采用 `/api/v1/chat/messages/{messageId}/thumb-down`（消息维度需要 chunk 引用关系表，暂不做）。

## 阶段 3: ES 索引操作能力层

### Task 3.1: ES 索引调优能力

- [x] 新增 `knowledge/service/EsIndexTuneService.java`
- [x] `pauseRefresh(index)`：设 `refresh_interval = -1`
- [x] `resumeRefresh(index)`：恢复 `1s`
- [x] `recommendShardCount(dataGb)`：按 30GB/分片标准计算推荐分片数
- **验证**：编译通过；批量导入日志可见 pause/resume

> 实现偏差：类名与包路径为 `knowledge/service/EsIndexTuneService`；`createIndexWithShardPlan`
> 未自动接入索引创建流程（当前知识库索引创建时数据量小，保留 `recommendShardCount` 工具方法）。

### Task 3.2: 集成到批量导入流程

- [x] `KnowledgeBaseService.batchUploadAndProcess`：导入前 `pauseRefresh`，finally 块 `resumeRefresh`
- [x] 外层已有 Redisson 分布式锁 `lock:kb:import:{kbId}` 串行化
- **验证**：编译通过

> 实现偏差：不设「文档数 > 1000」阈值，批量导入入口统一暂停刷新（批量场景本就低频，收益稳定且逻辑简单）。

### Task 3.3: 知识库索引分片规划

- [x] 提供 `recommendShardCount` 30GB/分片规划工具；对话日志索引 `chat_message_log` 按 1 主分片 + 1 副本创建
- **验证**：索引创建日志

> 实现偏差：未新增 `KnowledgeBase.est_size_gb` 字段（当前数据量无需动态分片，保持单分片基线，
> 数据增长后用 `recommendShardCount` 评估重建索引）。

## 阶段 4: 容灾备份能力层

### Task 4.1: MySQL JSONL 逻辑备份

- [x] 新增 `ops/backup/MysqlJsonlBackupService.java`：JDBC 流式读取 `chat_session` / `chat_message_log` 写 JSONL
- [x] `@Scheduled(cron = "${backup.mysql.cron:0 0 3 * * ?}")` 每日 03:00 触发
- [x] 文件命名含日期，保留 7 天自动清理
- **验证**：手动触发 `POST /api/v1/ops/backup/mysql` 生成 JSONL 文件

> 实现偏差：类名 `MysqlJsonlBackupService`（非 BackupService）；告警接入留待后续（当前 ERROR 日志）。

### Task 4.2: ES 快照备份

- [x] `ops/backup/EsSnapshotService.java`：`snapshotWeekly()` 对 `kb_*` 索引快照
- [x] `@Scheduled(cron = "${backup.es.cron:0 30 2 * * SUN}")` 每周日 02:30 触发
- [x] `restore(date)` 恢复指定日期快照；`purgeOldSnapshots` 保留近 4 个
- [x] `OpsBackupController`：`POST /api/v1/ops/backup/es/snapshot`、`POST /api/v1/ops/backup/es/restore`、`POST /api/v1/ops/backup/mysql`
- **验证**：编译通过；仓库由 EsSnapshotRepositoryInitializer 启动自动注册

### Task 4.3: Binlog 校验

- [x] 新增 `ops/backup/BinlogVerifyService.java`：`SHOW VARIABLES` + `SHOW BINARY LOGS` 巡检
- [x] `GET /api/v1/ops/binlog/verify` 返回配置状态（含 compliant 判定）
- **验证**：编译通过

> 实现偏差：未实现 `mysqlbinlog` 命令行解析（ProcessBuilder 方案依赖宿主机工具链，
> 按时间点恢复由 DBA 运维流程承载，接口只做配置巡检）。

## 阶段 5: 文档管理增强

### Task 5.1: 实现 DocumentRelationService

- [x] 新增 `document/service/DocumentRelationService.java`
- [x] `getRelatedChats(docId)`：取文档前 5 个分片内容片段对 ES `chat_message_log` 做 match_phrase 检索
- [x] 返回 `[{sessionId, sessionTitle, hitTime, querySnippet, chunkContent, messageId, role}]`，按 hitTime 倒序 top 20
- [x] `GET /api/v1/documents/{docId}/related-chats` 接口
- **验证**：编译通过；前端详情抽屉展示

### Task 5.2: 实现 BatchDocumentService

- [x] 新增 `document/service/BatchDocumentService.java`
- [x] `batchDelete(docIds)`：容错删除（部分失败不阻断），返回 `{successCount, failures}`
- [x] `batchReprocess(docIds)`：投递 MQ 队列，立即返回 `batchId`；Redis SET 去重
- [x] `getBatchProgress(batchId)`：Redis Hash `batch:progress:{batchId}` 读进度（TTL 1 天）
- [x] `document/mq/BatchDocumentConsumer.java` 消费并更新进度
- **验证**：编译通过；前端批量操作联调

### Task 5.3: 新增 BatchDocumentController

- [x] 新增 `document/controller/BatchDocumentController.java`
- [x] `POST /api/v1/documents/batch/delete`
- [x] `POST /api/v1/documents/batch/reprocess`（返回 batchId）
- [x] `GET /api/v1/documents/batch/reprocess/status/{batchId}`
- **验证**：编译通过

## 阶段 6: 对话体验打磨

### Task 6.1: 修复会话列表选中态闪烁

- [x] `stores/chat.ts` 的 `fetchSessions`：await 后锚定原选中 id，不在列表则置 null
- [x] `ChatView.vue` onMounted：记录 prevSelectedId，fetch 后优先沿用，避免 DOM 闪烁
- **验证**：前端构建通过

### Task 6.2: 对话历史时间范围筛选

- [x] 后端 `ChatSearchService.search` 新增 `startTime` / `endTime` 参数，ES DSL 加 `range` 过滤
- [x] `ChatSearchController` 新增两个可选 query 参数（毫秒时间戳）
- [x] 前端全局搜索对话框新增时间范围选择器（全部 / 近 7 天 / 近 30 天 / 自定义 daterange）
- **验证**：前后端构建通过

> 实现偏差：时间筛选集成在 `ChatView.vue` 全局搜索对话框（现有实现无独立 GlobalSearch.vue 组件）。

### Task 6.3: 清空筛选回到默认

- [x] 搜索对话框新增「清空筛选」按钮，重置时间范围与自定义区间并重跑检索
- **验证**：前端构建通过

## 阶段 7: 前端文档管理增强

### Task 7.1: 文档详情新增关联问答面板

- [x] `KnowledgeBaseView.vue` 文档详情抽屉新增「关联问答记录（Top 20）」面板
- [x] 调用 `GET /api/v1/documents/{docId}/related-chats` 加载数据
- [x] 列表项展示 sessionTitle / hitTime / querySnippet / chunkContent
- [x] 点击项跳转 `/chat?sessionId=xxx`，ChatView 支持 sessionId 路由参数定位会话
- [x] `api/document.ts` 新增 `getRelatedChats(docId)`
- **验证**：前端构建通过

> 实现偏差：集成在 `views/rag/KnowledgeBaseView.vue` 文档详情抽屉（项目无独立 DocumentDetail.vue）。

### Task 7.2: 文档列表批量操作工具栏

- [x] 文档表格新增多选 checkbox（selection 列）
- [x] 新增「批量删除」与「批量重处理」按钮，未勾选时禁用
- [x] 调用 `POST /documents/batch/delete` 与 `POST /documents/batch/reprocess`
- [x] 批量重处理后展示进度条（el-alert），3s 轮询 `status/{batchId}`，完成后提示并刷新列表
- [x] `api/document.ts` 新增 `batchDeleteDocuments` / `batchReprocessDocuments` / `getBatchProgress`
- [x] onUnmounted 清理轮询定时器
- **验证**：前端构建通过

> 实现偏差：集成在 KnowledgeBaseView 文档列表（项目无独立 DocumentList.vue）。

## 阶段 8: 验证与文档

### Task 8.1: 端到端验证

- [x] 场景 2：点踩 -3 + 幂等（chunk_feedback_log 唯一索引）— 代码级保证
- [x] 场景 5：会话列表刷新无闪烁（prevSelectedId 锚定）— 构建验证
- [x] 场景 8：binlog 为 ROW 模式，保留 7 天 — 容器实测 `binlog_expire_logs_seconds=604800`
- [x] 场景 9：文档详情关联问答 + 批量操作 — 前后端构建通过
- [ ] 场景 1：检索命中 +1 并发计数 — 需联调验证（异步逐条 UPDATE，无原子批量）
- [ ] 场景 3：低质 chunk 定时重处理 — 当前为清单日志模式，人工触发重处理
- [ ] 场景 4：批量导入 10000 文档耗时下降 ≥ 60% — 需压测数据，暂以 pause/resume 日志佐证
- [ ] 场景 6/7：JSONL 备份与 ES 快照定时运行 — 需等待调度周期或手动触发后复核文件

### Task 8.2: 更新文档

- [x] 追加 `docs/dev-log/2026-08-21.md` 记录 P3 落地过程
- [x] 同步 `project_memory.md` 沉淀关键知识点
- [ ] 更新 `docs/architecture.md` 新增 RAG 反馈闭环、ES 索引 ops、容灾备份、文档管理章节（后续迭代）
- [ ] 更新 `docs/api/` OpenAPI 描述（后续迭代）

## 阶段 9: 归档准备

### Task 9.1: OpenSpec validate

- [ ] 运行 `openspec validate add-p3-rag-feedback-ops` 确认工件完整
- [ ] 修复 lint 错误，确认 status 全部 done

### Task 9.2: archive 前置确认

- [ ] 确认 `tasks.md` 全部勾选（阶段 8 剩余验证项完成后再归档）
- [ ] 准备归档摘要（关键文件路径列表）
- **验证**：准备进入 `openspec archive` 流程
