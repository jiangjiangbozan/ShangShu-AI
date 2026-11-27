# 百炼额度排期与语料资产方案

> 场景：`qwen3.7-text-embedding-flash` 与 `qwen3.7-text-rerank` 额度即将到期，需要把剩余额度转成**一次性、可沉淀、可复用**的资产，而不是消耗在线上调用里。
> 数据基准：2026-11-27 实测单价 + 知识库入库统计（两轮批量任务累计）。

## 1. 额度现状

用户在 2026-11-27 两次给出的剩余额度与本次消耗：

| 模型 | 第二轮起点（控制台） | 本轮消耗 | 推算剩余 |
|---|---|---|---|
| qwen3.7-text-embedding-flash | 360,920 | 83,731（批次 D 51 篇入库） | **约 277,000** |
| qwen3.7-text-rerank | 502,220 | 0（本轮未调用） | **约 502,000** |

两轮累计消耗：embedding 约 355,000（批次 C 入库 137,175 + kb4 重嵌入 134,484 + 批次 D 入库 83,731），rerank 约 365,000（探针 16K + 错误库评测 89K + kb1 50 题正式评测 261K）。

> 本机 `sys_llm_log` 不记录 embedding / rerank 调用，额度以百炼控制台为准；上表按接口 `usage.total_tokens` 与切片 `token_count` 累加。

## 2. 单价实测（`dev/rag-eval/probe_usage.py`）

| 场景 | 输入规模 | 实测 token | 备注 |
|---|---|---|---|
| rerank（旧配置） | 10 候选 × 1,200 字符 | 7,710 | 每候选约 771 token |
| rerank（新配置） | 10 候选 × 800 字符 | **5,210** | 省 32.4%，判别结论一致 |
| rerank（小批量） | 6 候选 × 800 字符 | 3,126 | 每候选约 521 token |
| embedding（纯中文） | 1,200 中文字符 | 766 | 0.638 token/字符 |
| embedding（K8s 中英语料） | 721,708 字符（kb5 全量） | 220,906 | 0.306 token/字符，英文与代码 token 效率更高 |

结论：**中文纯文本 embedding 约 0.64 token/字符，含英文/代码的技术文档约 0.31 token/字符**。

## 3. 800 字瘦身的质量验证

同一套 50 题（kb1）、同一链路，只改 `max-candidate-chars`：

| 配置 | Recall@5 | MRR | nDCG@5 | 命中率 | 单次精排 token |
|---|---|---|---|---|---|
| 1,200 字符 | 0.940 | 0.845 | 0.870 | 94% | 7,710 |
| **800 字符** | **0.960** | **0.857** | **0.884** | **96%** | **5,210** |

结论：截断到 800 字符不仅省 32.4% token，指标还小幅上升（rerank 判别更聚焦于开场语义），已固化为配置默认值。详细数据见 `rag-eval-slim-report.md`。

## 4. 已沉淀的语料资产

| 知识库 | 文档 | 切片 | token | 来源 |
|---|---|---|---|---|
| kb1 测试知识库 | 2 | 70 | 12,486 | 编译原理实验报告（评测基准库） |
| kb3 技术文档库（批次 A） | 27 | 338 | 56,170 | 项目自文档（架构/部署/接口/评测） |
| kb4 官方技术文档库（批次 B） | 33 | 747 | 122,968 | Spring AI / MyBatis-Plus / Elasticsearch / RabbitMQ 官方仓库 |
| kb5 中文技术文档库（批次 C + D） | 123 | 1,264 | 220,906 | Kubernetes 中文官方文档（CC BY 4.0） |
| **合计** | **185** | **2,419** | **412,530** | 4 个有效知识库 |

ES 索引 `ai_agent_knowledge` 同步 2,419 条向量（768 维、cosine、int8_hnsw），与 MySQL 切片数完全对账。

### 4.1 修复记录：kb4 向量空间错配

批次 B 入库时后端仍用 Ollama `nomic-embed-text`，747 条 kb4 向量与线上 DashScope 查询向量不在同一空间，检索必然失效（实测 nDCG 全为 0）。已用 `reembed.py --kb-id 4` 原地重算 747 条（492,982 字符 / 134,484 token / 20 秒），分片 id 与文本不变，历史评测集仍可用。

### 4.2 尚未执行的评测（额度已预留）

kb5 的 80 题中文评测集已生成（`dev/rag-eval/dataset-kb5.jsonl`，题目由 qwen-plus 直调生成，不占用两个向量模型额度），但**尚未跑全链路**。按 5,210 token/题估算，跑完约消耗 **416,800 rerank**，跑后剩余约 85,000。

跑法：

```bash
cd dev/rag-eval
python run_eval.py --kb-id 5 --dataset dataset-kb5.jsonl --output results-kb5.json \
  --report ../../docs/platform/testing/rag-eval-kb5-report.md
```

如果希望保留更多线上缓冲，可先跑 40 题（约 208K），留 294K。

## 5. 剩余额度与后续选项

当前剩余：embedding 约 277K、rerank 约 502K。

- **embedding 277K**：可再入库约 90 万字符中英语料（约 100~150 篇），候选源仍是 Kubernetes 中文站剩余分区；也可留作后续文档增量的常态化开销。
- **rerank 502K**：优先把 kb5 的 80 题评测跑完（416K），这是目前已生成但未验证的唯一评测资产；剩余约 85K 作为缓冲。
- 不建议为了"用尽"而降低候选数或缩短截断——会直接损伤检索质量。

## 6. 明确不做（避免额度打水漂）

- **不做 N² 两两相似度**：2,419 切片两两比对约 292 万次调用，额度瞬间烧光且无可复用产物。
- **不把 rerank 挂在每轮对话**：单次 5,210 token，1,000 轮就是 5.2M，额度耗尽后线上直接降级。
- **不指望这两个模型生成内容**：它们是表征模型与判别模型，只能产出向量与相关性分数。

## 7. 复现与成本控制

```powershell
# 抓取（增量抓取自动跳过已抓页面，可按分区筛选）
python dev/rag-corpus/fetch_k8s_zh.py --limit 60 --sections tasks,tutorials

# 增量入库（--from-order 只传新增文件）
python dev/rag-corpus/upload_corpus_c.py --from-order 86

# 指定知识库原地重嵌入
python dev/rag-eval/reembed.py --kb-id 4

# 实测单价（消耗约 1.7 万 token）
python dev/rag-eval/probe_usage.py
```

成本控制要点：`rag.rerank.max-candidate-chars` 已从 1,200 降到 800，单次精排 token 下降 32%；候选数 `candidate-n` 保持 10，不建议再降（会损失召回上限）。
