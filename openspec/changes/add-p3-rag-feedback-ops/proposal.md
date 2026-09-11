## Why

P2 阶段（add-p2-cache-search-rewrite）落地后会话历史、RAG 召回、MQ 链路与全局检索已闭环，但平台进入长期运营阶段后会面临以下持续优化诉求：

1. **RAG 召回质量缺少反馈闭环**：分片命中次数无统计，低质分片长期堆积无人重处理，召回准确率随数据膨胀缓慢下降。
2. **ES 索引在数据量增长后性能退化**：批量导入期间 refresh_interval 默认 1s 导致写入吞吐瓶颈，分片数与副本数未按容量规划。
3. **对话列表选中态闪烁**：刷新会话列表后计算属性短暂返回 null，DOM 闪烁影响体验。
4. **缺少容灾备份**：MySQL 单点故障或误删文档无法快速恢复，无 binlog 时间点回滚能力。
5. **文档管理效率低**：详情页无法看到文档被哪些会话引用，批量操作缺失，运营效率受限。

本 change（P3 长期优化）通过 5 个能力建设形成「检索-反馈-优化」自闭环、提升 ES 容量与稳定性、打磨对话体验、构建容灾体系、增强文档管理，支撑平台长期迭代。

## What Changes

### 1. RAG 检索反馈闭环
- `knowledge_chunk` 表新增 `hit_count` 命中计数字段（Flyway 迁移脚本）
- 检索命中后异步 +1；用户点踩反馈 -3
- 新增定时任务每周触发低分、低命中分片的重新解析与向量化

### 2. ES 索引性能与容量优化
- 批量导入文档时动态设置 `refresh_interval = -1`，导入完成后恢复默认 `1s`
- 按 30GB/分片标准规划知识库索引分片数
- 副本数按业务数据量动态调整（开发环境 1 副本，生产环境 2 副本）

### 3. 对话体验细节打磨
- 修复会话列表刷新后选中态闪烁：`fetchSessions` 完成后主动锚定当前会话 ID
- 配合 ES 全局检索，对话历史支持按时间、关键词精细化筛选

### 4. 数据备份与容灾体系
- MySQL 每日定时导出会话与消息为 JSONL 文件，保留 7 天
- ES 每周对知识库向量索引做快照，支持误删恢复
- MySQL binlog 开启 row 模式，保留 7 天，支持按时间点回滚

### 5. 文档管理增强能力
- 文档详情页新增「关联问答记录」展示
- 支持批量删除、批量重处理文档

## Capabilities

### New Capabilities
- `rag/feedback-loop`: RAG 检索命中计数、点踩反馈与低质分片定时重处理闭环
- `es/index-ops`: ES 索引 refresh_interval 动态调整与分片容量规划
- `ops/backup-recovery`: MySQL JSONL 备份、ES 快照、binlog 规范的容灾体系
- `document/management-enhance`: 文档关联问答记录展示与批量操作

### Modified Capabilities
- `chat/session-list`: 修复选中态闪烁，新增时间 / 关键词筛选能力

## Impact

- **数据库**：
  - `knowledge_chunk` 新增 `hit_count INT DEFAULT 0` 字段（Flyway V20260901__add_hit_count_to_knowledge_chunk.sql）
  - MySQL my.cnf 开启 `log_bin` `binlog_format=ROW` `binlog_expire_logs_seconds=604800`
- **后端代码**：
  - `api/src/main/java/com/ai/api/knowledge/` 新增 `feedback/ChunkFeedbackService.java`、`feedback/ChunkReprocessScheduler.java`、`ops/EsIndexOpsService.java`、`ops/BackupService.java`、`ops/BinlogVerifyService.java`
  - `api/src/main/java/com/ai/api/document/` 新增 `batch/BatchDocumentService.java`、`relation/DocumentRelationService.java`
  - 修改 `KnowledgeBaseService.retrieve` 检索命中后异步调用 `chunkFeedbackService.incrHit(chunkId)`
- **前端代码**：
  - `ai-agent-web/src/views/chat/ChatView.vue` 修复 fetchSessions 后锚定当前 sessionId
  - `ai-agent-web/src/views/document/DocumentDetail.vue` 新增「关联问答记录」面板
  - `ai-agent-web/src/views/document/DocumentList.vue` 新增批量操作工具栏
- **配置**：
  - `application-dev.yml` 新增定时任务 cron 与备份路径配置
  - Docker Compose 新增 MySQL `mysqld` 配置文件挂载
  - ES 新增 snapshot repository 注册
- **依赖**：无新引入依赖（复用现有 Spring Boot starters）
- **关联 change**：依赖前序 change `add-p2-cache-search-rewrite`（ES 双写与全局检索能力先行落地）

## 非目标

- 不做 RAG 评测体系的完整搭建（含 recall@k、precision@k 自动化评测流水线）—— 留待 V2 评测平台
- 不实现 ES 跨集群灾备（CCSR）—— 仅做单集群快照
- 不做 MySQL 主从复制与切换 —— 仅做逻辑备份与 binlog
- 不实现文档版本管理 —— 仅做批量删除与重处理
- 不引入新的存储引擎（如 ClickHouse）—— 仅在现有 MySQL / ES 上扩展

## 验收标准

1. 检索命中后 `knowledge_chunk.hit_count` 异步 +1，无并发计数丢失
2. 用户点踩后 `hit_count` -3，触发重处理阈值（hit_count < 5 且 score < 0.3）的分片在每周日凌晨被重处理
3. 批量导入 10000 文档耗时较优化前下降 ≥ 60%（refresh_interval = -1 期间）
4. 会话列表刷新后无 DOM 闪烁，选中态保持稳定
5. MySQL JSONL 备份每日凌晨生成，7 天前的备份自动清理
6. ES 知识库向量索引每周快照成功，可通过 snapshot API 恢复
7. MySQL binlog 为 ROW 模式，保留 7 天，可通过 `mysqlbinlog --start-datetime` 解析
8. 文档详情页能看到该文档被哪些会话命中引用，点击可跳转到对应会话
9. 批量删除 / 重处理 10 个文档操作响应 < 3s
