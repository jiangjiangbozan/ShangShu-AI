# RAG 检索链路评测报告

> 生成时间：2026-11-27 19:42（Asia/Shanghai）
> 知识库：`1` · 数据集：`50/50` 题 · topK：5 · RRF k：默认(60) · 接口：`POST /api/v1/knowledge/{kbId}/retrieve-test`（不走缓存）
> 当前链路：百炼 `qwen3.7-text-embedding-flash`（768 维）+ 四路召回 + RRF + 百炼 `qwen3.7-text-rerank`

## 指标总览

| 阶段 | Recall@5 | MRR | nDCG@5 | 命中率 |
|---|---|---|---|---|
| 纯向量 | 0.820 | 0.700 | 0.731 | 82.0% |
| 纯关键词 | 0.840 | 0.615 | 0.672 | 84.0% |
| 向量+关键词+RRF | 0.940 | 0.744 | 0.794 | 94.0% |
| +Rerank | **0.940** | **0.845** | **0.870** | **94.0%** |

平均服务端链路耗时 **13262 ms**（QueryRewrite + 四路召回 + RRF + Rerank 各步之和），客户端整体耗时 **13297 ms**。

## 延迟分布

| 指标 | P50 | P95 | 平均 |
|---|---|---|---|
| 服务端链路 (ms) | 12751 | 18374 | 13262 |
| 客户端整体 (ms) | 12770 | 18399 | 13297 |

> 瓶颈在本地 Ollama `qwen3:8b` 的 QueryRewrite（单题十秒级），向量化与精排本身仅数百毫秒：rerank 单次 242–299 ms。

## Rerank 调用状态

`success` 50 次 · `disabled` 0 次 · `degraded` 0 次 · `missing` 0 次

> `disabled`：`rag.rerank.enabled=false` 或未配置 `rag.rerank.base-url`（RRF 原序直出）；`degraded`：rerank 服务调用/解析失败自动降级。状态由 retrieve-test trace 的 `rerankStatus`/`rerankReason` 透出。

## 柱状图

### Recall@5

- 纯向量：`█████████████████████████░░░░░ 0.820`
- 纯关键词：`█████████████████████████░░░░░ 0.840`
- 向量+关键词+RRF：`████████████████████████████░░ 0.940`
- +Rerank：`████████████████████████████░░ 0.940`

### nDCG@5

- 纯向量：`██████████████████████░░░░░░░░ 0.731`
- 纯关键词：`████████████████████░░░░░░░░░░ 0.672`
- 向量+关键词+RRF：`████████████████████████░░░░░░ 0.794`
- +Rerank：`██████████████████████████░░░░ 0.870`

## 演进对比（同一数据集、同一知识库，仅换模型/开关）

| 版本 | 向量化 | 精排 | 纯向量 Recall | RRF Recall | +Rerank Recall | +Rerank MRR | +Rerank nDCG | 命中率 |
|---|---|---|---|---|---|---|---|---|
| 2026-11-19 基线 | Ollama nomic-embed-text | 未生效 | 0.480 | 0.780 | 0.480（=纯向量，链路降级） | 0.350 | 0.383 | 48% |
| 2026-11-27 首轮 | Ollama nomic-embed-text | 百炼 qwen3.7-text-rerank | 0.480 | 0.780 | 0.820 | 0.745 | 0.764 | 82% |
| 2026-11-27 P1 | 百炼 qwen3.7-text-embedding-flash | 百炼 qwen3.7-text-rerank | **0.820** | **0.940** | **0.940** | **0.845** | **0.870** | **94%** |

三条结论：

1. **精排是纯增益**：同向量化下接入 rerank，Recall 0.780 → 0.820、MRR 0.491 → 0.745、nDCG 0.563 → 0.764。
2. **向量化是最大瓶颈**：只换 embedding（nomic → 百炼 qwen3.7），纯向量召回从 0.480 翻到 0.820，RRF 阶段从 0.780 到 0.940——中文语义匹配质量决定了召回上限。
3. **两者叠加接近饱和**：全链路 94% 命中、nDCG 0.870，剩余误差主要来自 QueryRewrite 改写偏差与分片边界。

## 复现方式

```bash
cd dev/rag-eval
# 数据集重建（可选，已有 dataset.jsonl 时跳过）
python build_dataset.py --kb-id 1 --count 50
# 评测（当前 embedding 后端下）
python run_eval.py --kb-id 1 --top-k 5 --output p1-results.json --report p1-embed-report.md
# 更换 embedding 后端后，先用 reembed.py 原地重算向量，保证旧 chunk id 仍有效
python reembed.py --index ai_agent_knowledge
```
