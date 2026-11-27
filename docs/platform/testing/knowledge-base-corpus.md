# 知识库语料与切片统计

> 统计时间：2026-11-27 · 数据源：`ai_agent`（MySQL）+ `ai_agent_knowledge`（Elasticsearch）
> 口径：仅统计 `deleted=0` 的有效记录；切片 token 为分片入库时写入的 `token_count`

## 结论

- 全库 **185 篇文档 / 2,419 条切片 / 412,530 token**，覆盖项目自文档、四条技术栈官方文档与 Kubernetes 中文文档。
- **链路零丢失**：MySQL 切片数 2,419 与 ES 向量数 2,419 完全一致，「解析 → 切片 → 向量化 → 写索引」四步对账平衡。
- **切片粒度健康**：空切片 0；长度 12–997 字符、均值 684 字符、平均 171 token，落在 `chunkSize=800` + `chunkOverlap=200` 的设计区间。
- 换向量模型（nomic → 百炼 qwen3.7）并把 rerank 候选截断降到 800 字后，50 题全链路 **Recall@5 0.960 / MRR 0.857 / nDCG@5 0.884 / 命中率 96%**（详见 `rag-eval-slim-report.md`）。

## 一、各知识库规模

| id | 知识库 | 文档数 | 切片数 | 平均 token/片 | 总 token |
|---|---|---|---|---|---|
| 1 | 测试知识库（编译原理实验报告） | 2 | 70 | 178 | 12,486 |
| 3 | 技术文档库（批次 A） | 27 | 338 | 166 | 56,170 |
| 4 | 官方技术文档库（批次 B） | 33 | 747 | 165 | 122,968 |
| 5 | 中文技术文档库（批次 C + D） | 123 | 1,264 | 175 | 220,906 |
| — | **合计** | **185** | **2,419** | **≈171** | **412,530** |

## 二、切片质量

| 指标 | 数值 |
|---|---|
| 最短切片 | 12 字符 |
| 平均切片长度 | 684 字符 |
| 最长切片 | 997 字符 |
| 空切片 | 0 |
| 平均 token/片 | 171 |
| kb5 单篇切片数 | 最少 3 / 平均 10.3 / 最多 25 |

分片器优先在句末 / 段末断开，最长 997 字符略超 800 窗口属正常；均值 684 表示切点利用充分，没有产生大量碎片。

## 三、语料构成

### 批次 A：项目自文档（27 篇 / 338 切片 / 5.6 万 token）

取自仓库 `docs/`：README、架构说明、工作流节点指南、四份 API 文档、评测报告，以及 `docs/kb/` 下 17 篇技术栈知识文档（含 ADR、Agent、前后端实践）。语料直接来自仓库文件，可随代码演进重新入库。

### 批次 B：官方技术文档（33 篇 / 747 切片 / 12.3 万 token / 36.4 万字符）

全部取自官方仓库的文档源（AsciiDoc / Markdown 原文），不是二手博客；每篇记录仓库、分支、路径、许可证与抓取时间，清单见 `dev/rag-corpus/MANIFEST.md`。

| 技术栈 | 篇数 | 字符数 | 覆盖内容 |
|---|---|---|---|
| Spring AI | 18 | 250,218 | RAG 总览与模块、ChatClient、Advisor、Tool Calling、结构化输出、可观测性、LLM-as-Judge、ChatMemory、Prompt、语义缓存、多模型 |
| MyBatis-Plus | 12 | 49,359 | 快速开始、安装、逻辑删除、自动填充、主键策略、批处理、高级特性、SQL 注入器、类型处理器、分页插件、多租户、乐观锁 |
| RabbitMQ | 3 | 64,727 | AMQP 概念模型、队列特性、消费者预取与限流 |

### 批次 C：Kubernetes 中文文档 concepts（72 篇 / 775 切片 / 13.7 万 token / 46.9 万字符）

抓取 `kubernetes.io/zh-cn` 的 `docs/concepts` 分区（Pod、控制器、存储、网络、安全、调度、集群管理等核心概念页），HTML 正文提取，许可证 CC BY 4.0。

### 批次 D：Kubernetes 中文文档 tasks/tutorials（51 篇 / 489 切片 / 8.4 万 token / 25.2 万字符）

继续抓取 `docs/tasks` 与 `docs/tutorials` 分区（日志与监控、集群管理、kubectl 安装、DaemonSet/PDB/TLS/拓扑管理等操作型指南），与批次 C 合并为 kb5「中文技术文档库」，共 **123 篇 / 1,264 切片 / 220,906 token / 72.2 万字符（其中中文 23.7 万）**。

> 批次 C/D 语料本体不入 Git（可由 `dev/rag-corpus/fetch_k8s_zh.py` 一键复现），仓库只保留脚本、清单与统计，避免站点内容膨胀。

## 四、新增语料的流程

```bash
# 1. 抓取（增量抓取会自动跳过已抓页面并按分区筛选）
cd dev/rag-corpus && python fetch_k8s_zh.py --limit 60 --sections tasks,tutorials

# 2. 入库（按名称复用知识库；--from-order 只传新增文件，避免重复入库）
python upload_corpus_c.py --from-order 86

# 3. 若更换了向量模型，按知识库定点重算历史分片向量
cd ../rag-eval && python reembed.py --kb-id 4

# 4. 对账：MySQL 切片数应等于 ES 向量数
curl "http://localhost:9200/ai_agent_knowledge/_count"
```
