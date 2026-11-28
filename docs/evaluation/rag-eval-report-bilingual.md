# RAG 检索评测报告（双语）

> English version below / 英文版见下方 [English Version](#english-version)

- 版本：v1.0（2026-11-28）
- 项目：ShangShu-AI 跨语言知识库（ShangShu-AI Cross-Language Knowledge Base）
- 数据来源：`dev/rag-eval/`（results-*.json、rerank-report.md、p1-embed-report.md、rerank-calib-*.jsonl）与 `docs/platform/testing/` 各轮报告，全部指标均为真实评测产出，无估算值。
- 声明：本报告文本由人工撰写，撰写过程未调用任何大模型（符合 `docs/platform/testing/model-usage-policy.md` 的额度口径）。

---

## 一、评测目标与范围

围绕四步跨语言路线中的「跨语言检索」能力，回答三个问题：

1. 当前混合检索链路（多问句改写 → 多路召回 → RRF 融合 → Rerank 精排）在中文语料上效果如何？
2. 语料风格（官方术语化文档 vs 用户自然口语问法）对召回的冲击有多大？
3. 跨语言检索（外文 query → 译中 → 召回）上线前，系统还缺什么？

## 二、评测基础设施

| 组件 | 说明 |
| --- | --- |
| 评测入口 | 后端 `retrieveWithTrace` 全链路追踪接口（originalQuery / rewrittenQueries / vectorRecall / keywordRecall / rrfMerged / rerankStatus / reranked / finalDocuments） |
| 驱动脚本 | `dev/rag-eval/run_eval.py`：登录评测账号（rageval）→ 逐题调 trace 接口 → 落盘 results-*.json |
| 数据集 kb1 | 自项目文档语料 50 题（中英混合术语问句） |
| 数据集 kb5-a | K8s 中文官方文档 85 篇 / 800 切片，术语化问句 80 题 |
| 数据集 kb5-b | 同语料，自然口语问法 30 题（A/B 评测用） |
| 指标口径 | Recall@5（前 5 是否含目标文档）、MRR（目标文档平均倒数排名）、nDCG@5（分级相关性折算）、命中率（全题至少命中一次的比例）；聚合方式取各题均值 |
| rerank 标定 | `rerank-calib-10/80.jsonl` + hard14-55 / hard9-80 难例集，用于阈值实验（0.5 收口依据） |

## 三、六轮指标总览

格式：`Recall@5 / MRR / nDCG@5 / 命中率`。

### kb1（自项目文档语料，50 题）

| 轮次 | 召回配置 | Recall@5 | MRR | nDCG@5 | 命中率 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 1. 基线（修复前） | 纯向量 | 0.480 | 0.350 | 0.383 | 48% | 单路语义召回不稳定 |
| 1. 基线（修复前） | 纯关键词 | 0.800 | 0.577 | 0.634 | 80% | BM25 在术语问句上占优 |
| 1. 基线（修复前） | RRF 融合 | 0.780 | 0.491 | 0.563 | 78% | 融合未带来增益 |
| 1. 基线（修复前） | RRF + Rerank | 0.820 | 0.745 | 0.764 | 82% | rerank 曾拖累 RRF（阈值未收口） |
| 2. rerank 阈值 0.5 收口 | RRF 融合 | 0.960 | 0.779 | 0.825 | 96% | 阈值收口后融合路大幅回升 |
| 2. rerank 阈值 0.5 收口 | RRF + Rerank | **0.960** | **0.857** | **0.884** | **96%** | MRR +0.078，rerank 反超并稳定增益 |
| 3. P1 百炼 embedding | RRF 融合 | 0.940 | 0.744 | 0.794 | 94% | 向量重嵌入后整体持平略降 |
| 3. P1 百炼 embedding | RRF + Rerank | 0.940 | 0.845 | 0.870 | 94% | 精排仍贡献主要排序增益，平均耗时 13262ms |

### kb5（K8s 中文官方文档 85 篇）

| 轮次 | 召回配置 | Recall@5 | MRR | nDCG@5 | 命中率 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 4. 术语化问句（80 题） | 纯向量 | 0.963 | 0.835 | 0.868 | 96.2% | 800 切片官方语料上向量路已很强 |
| 4. 术语化问句（80 题） | RRF 融合 | 0.975 | 0.833 | 0.870 | 97.5% | 命中率提升 1.3pp |
| 4. 术语化问句（80 题） | RRF + Rerank | **0.975** | 0.796 | 0.842 | **97.5%** | rerank success 80/80，MRR 略降；平均耗时 13856ms |
| 5. 自然口语问法（30 题） | 全配置 | 0.167 | 0.144~0.167 | ≈0.150 | 16.7% | 口语化问法与术语化文档存在领域鸿沟，全线崩 |
| 6. 自然问法 A/B（rewrite 对照） | QueryRewrite 开/关 | 0.167 | 0.167 | 0.167 | 16.7% | 两轮聚合指标完全一致，仅 P50 延迟 11217 vs 11951ms 与个别 chunk 顺序差异 |

## 四、关键发现

1. **Rerank 阈值收口是本阶段最大收益来源。** 修复前 rerank 分数直接并入会拖累 RRF（MRR 0.745 < 阈值收口后的 0.857）；以 `rerank-calib-*.jsonl` 难例集标定、把有效阈值定在 0.5 并写回配置后，rerank 从「拖累项」变为「稳定增益项」（kb1：MRR +0.078、nDCG@5 +0.059）。
2. **RRF 融合的价值在命中率而不在单指标。** 术语化问句上，RRF 将 kb5 命中率从 96.2%（纯向量）提升到 97.5%；kb1 上把 78% 拉到 96%（配合阈值收口）。BM25 关键词路对专有名词、精确编号问句是必要兜底。
3. **语料风格鸿沟比模型参数更致命。** 同一语料、同一链路，术语化问句 0.975 vs 自然口语问法 0.167：用户不会按文档标题的方式提问。缓解手段按优先级：① 语料增强（已做：255 篇双语摘要回填 + 摘要/关键词/QA 对入向量库）；② query 改写对自然问法**无显著提升**（A/B 两轮指标完全一致），不要指望改写模型弥合领域鸿沟；③ 扩充口语化平行语料。
4. **QueryRewrite A/B 结论明确。** 在自然问法场景，开/关改写的聚合指标（Recall/MRR/nDCG/命中率）完全一致，仅 P50 延迟从 11951ms 降至 11217ms（关闭改写更快）。改写保留用于多问句扩展场景，不作为自然问法的解法。
5. **rerank 在 kb5 上收益为负（MRR -0.037）但命中率不变。** 推测与 rerank 模型对长技术文档的打分偏置有关，后续应在 kb5 难例上单独标定阈值，而不是沿用 kb1 的 0.5。

## 五、跨语言就绪度

四步跨语言路线的当前状态（③ 为本报告对应的能力项）：

| 步骤 | 能力 | 状态 | 依赖额度 |
| --- | --- | --- | --- |
| ① 消息翻译 | qwen-mt-uni 文本翻译（聊天消息、批量分批 20 条） | ✅ 已上线 | mt-uni（1M 字符） |
| ② 双语摘要 | 文档英文摘要 `summary_en`，255/255 篇回填（输入 24,346 字符 / 输出 78,458 字符 / 13 次请求） | ✅ 已上线 | mt-uni |
| ③ 跨语言检索 | `CrossLanguageQueryService`：外文 query 检测（CJK 判定）→ qwen-mt-uni 译中 → 改写/召回/精排全链路用译后 query → trace 透出 `crossLanguage`（status/detectedSourceLang/translatedQuery）→ 片段预览按需回译 | ✅ 代码已合入，待 embedding 额度恢复后 E2E | mt-uni（query 翻译 + 预览回译） |
| ④ 实时同传 | qwen3.8-livetranslate-flash-realtime WebSocket 代理（后端 → Android/Web），20.3 秒中文语音实测 308 tokens（≈7 token/秒） | ✅ 已上线 | livetranslate（1M） |

**③ 的设计要点**（对应 commit：`add-cross-language`）：

- 翻译发生在**向量化之前**：只调用 mt-uni，不触碰 embedding 额度；翻译失败自动降级原文检索，不阻断主链路。
- 缓存 key 仍为原始 query（`rag.hits` 5 分钟缓存），同外文 query 的重复翻译由 mt-uni 进程内缓存兜底。
- 检索测试 trace 新增 `crossLanguage` 字段与 `backTranslateTookMs`；片段回译仅处理 ≤200 字符预览（vectorRecall/keywordRecall/rrfMerged/reranked 四路），成本可控，可用 `rag.cross-language.translate-results` 关闭。
- 配置：`rag.cross-language.enabled`（默认开）/ `target-lang`（默认 zh）/ `translate-results`（默认开）。

## 六、额度边界与待办

按 `docs/platform/testing/model-usage-policy.md` 当前额度口径：

- **禁用中**：`qwen3.7-text-embedding-flash`（入库/重嵌入/query 向量化全部禁止）、`qwen3.7-text-rerank` 额度为 0。因此本报告**不含**跨语言检索的实测指标；③ 的验证边界为「编译通过 + 后端启动」，E2E 留待额度恢复后统一执行。
- **额度恢复后待办**：
  1. 英文 query 评测集（kb1/kb5 各 30~50 题）跑通跨语言检索 E2E，验证「译中后召回 ≥ 原文直查基线」；
  2. kb5 rerank 阈值单独标定（见发现 5）；
  3. 自然口语平行语料扩充与复评（见发现 3）；
  4. Android 真机实时同传回归。

## 七、结论

混合检索链路在术语化问句上已达 **Recall@5 0.96~0.975 / 命中率 96~97.5%** 的可用水位；当前最大瓶颈是**自然口语问法（0.167）**与**跨语言实测缺位**。③ 跨语言检索代码已就位（译中 → 召回 → 回译，全程只用 mt-uni），待 embedding 额度恢复后即可用英文评测集完成闭环验证。

---

<a name="english-version"></a>

# RAG Retrieval Evaluation Report (English Version)

- Version: v1.0 (2026-11-28)
- Project: ShangShu-AI Cross-Language Knowledge Base
- Sources: all metrics come from real evaluation artifacts under `dev/rag-eval/` (results-*.json, rerank-report.md, p1-embed-report.md, rerank-calib-*.jsonl) and the per-round reports under `docs/platform/testing/`. No estimated values.
- Note: this report was written without invoking any LLM, in compliance with `docs/platform/testing/model-usage-policy.md`.

## 1. Scope

This report answers three questions for the cross-language retrieval capability (step ③ of the four-step roadmap):

1. How well does the hybrid retrieval pipeline (query rewrite → multi-lane recall → RRF fusion → rerank) perform on Chinese corpora?
2. How hard does corpus style (official terminology vs. colloquial user questions) hit recall?
3. Before cross-language retrieval (foreign query → translate to Chinese → retrieve) ships, what is still missing?

## 2. Evaluation Infrastructure

| Component | Detail |
| --- | --- |
| Entry point | Backend `retrieveWithTrace` full-chain trace (originalQuery / rewrittenQueries / vectorRecall / keywordRecall / rrfMerged / rerankStatus / reranked / finalDocuments) |
| Driver | `dev/rag-eval/run_eval.py`: login as eval account (rageval) → call trace per question → dump results-*.json |
| Dataset kb1 | 50 questions on self-project docs corpus (mixed CN/EN terminology queries) |
| Dataset kb5-a | K8s Chinese official docs, 85 docs / 800 chunks, 80 terminology-style questions |
| Dataset kb5-b | Same corpus, 30 colloquial questions (for the A/B test) |
| Metrics | Recall@5, MRR, nDCG@5, hit rate (per-question mean) |
| Rerank calibration | `rerank-calib-10/80.jsonl` + hard14-55 / hard9-80 hard-case sets, basis of the 0.5 threshold |

## 3. Metrics Summary

Format: `Recall@5 / MRR / nDCG@5 / hit rate`.

### kb1 (self-project docs, 50 questions)

| Round | Config | Recall@5 | MRR | nDCG@5 | Hit rate | Note |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Baseline (pre-fix) | Vector only | 0.480 | 0.350 | 0.383 | 48% | Unstable single-lane semantic recall |
| 1. Baseline (pre-fix) | Keyword only | 0.800 | 0.577 | 0.634 | 80% | BM25 wins on terminology queries |
| 1. Baseline (pre-fix) | RRF fusion | 0.780 | 0.491 | 0.563 | 78% | Fusion gained nothing yet |
| 1. Baseline (pre-fix) | RRF + Rerank | 0.820 | 0.745 | 0.764 | 82% | Rerank dragged RRF down (threshold uncalibrated) |
| 2. Rerank threshold 0.5 | RRF fusion | 0.960 | 0.779 | 0.825 | 96% | Fusion lane recovered after threshold fix |
| 2. Rerank threshold 0.5 | RRF + Rerank | **0.960** | **0.857** | **0.884** | **96%** | MRR +0.078; rerank turned into a stable gain |
| 3. P1 DashScope embedding | RRF fusion | 0.940 | 0.744 | 0.794 | 94% | Slightly flat after re-embedding |
| 3. P1 DashScope embedding | RRF + Rerank | 0.940 | 0.845 | 0.870 | 94% | Rerank still the main ranking gain; avg latency 13262ms |

### kb5 (K8s Chinese official docs, 85 docs)

| Round | Config | Recall@5 | MRR | nDCG@5 | Hit rate | Note |
| --- | --- | --- | --- | --- | --- | --- |
| 4. Terminology queries (80 q) | Vector only | 0.963 | 0.835 | 0.868 | 96.2% | Vector lane already strong on official corpus |
| 4. Terminology queries (80 q) | RRF fusion | 0.975 | 0.833 | 0.870 | 97.5% | Hit rate +1.3pp |
| 4. Terminology queries (80 q) | RRF + Rerank | **0.975** | 0.796 | 0.842 | **97.5%** | rerank success 80/80, MRR slightly down; avg 13856ms |
| 5. Colloquial queries (30 q) | All configs | 0.167 | 0.144~0.167 | ≈0.150 | 16.7% | Domain gap between colloquial queries and terminology docs collapses all lanes |
| 6. Colloquial A/B (rewrite on/off) | QueryRewrite on/off | 0.167 | 0.167 | 0.167 | 16.7% | Identical aggregate metrics; only P50 latency 11217 vs 11951ms and minor chunk-order diffs |

## 4. Key Findings

1. **The rerank threshold fix was the single biggest win.** Before calibration, merged rerank scores dragged RRF down (MRR 0.745 vs 0.857 after); calibrating on the `rerank-calib-*.jsonl` hard-case sets and gating at 0.5 turned rerank from a liability into a stable gain (kb1: MRR +0.078, nDCG@5 +0.059).
2. **RRF fusion mainly buys hit rate, not single-metric gains.** On terminology queries, RRF lifted kb5 hit rate from 96.2% (vector only) to 97.5%, and kb1 from 78% to 96% (with the threshold fix). The BM25 keyword lane remains a necessary fallback for proper nouns and exact identifiers.
3. **Corpus style gap is more lethal than model choice.** Same corpus, same pipeline: 0.975 on terminology queries vs 0.167 on colloquial ones — users don't ask questions in document-title style. Mitigations in priority order: ① corpus enrichment (done: 255 bilingual summaries backfilled; summaries/keywords/QA pairs indexed); ② query rewrite shows **no significant gain** for colloquial queries (A/B rounds identical) — do not rely on the rewriter to bridge the domain gap; ③ expand colloquial parallel corpora.
4. **QueryRewrite A/B verdict is clear.** On colloquial queries, aggregate metrics are identical with rewrite on/off; only P50 latency differs (11951ms → 11217ms with rewrite off). Keep rewrite for multi-query expansion, not as a colloquial remedy.
5. **Rerank is slightly negative on kb5 (MRR -0.037) with unchanged hit rate.** Likely scoring bias of the rerank model on long technical docs; kb5 needs its own threshold calibration instead of reusing kb1's 0.5.

## 5. Cross-language Readiness

Current state of the four-step cross-language roadmap (③ is the capability this report accompanies):

| Step | Capability | Status | Quota |
| --- | --- | --- | --- |
| ① Message translation | qwen-mt-uni text translation (chat messages, batches of 20) | ✅ Shipped | mt-uni (1M chars) |
| ② Bilingual summaries | English `summary_en` per document, 255/255 backfilled (24,346 chars in / 78,458 out / 13 requests) | ✅ Shipped | mt-uni |
| ③ Cross-language retrieval | `CrossLanguageQueryService`: foreign query detection (CJK check) → qwen-mt-uni translate to Chinese → rewrite/recall/rerank all run on the translated query → trace exposes `crossLanguage` (status/detectedSourceLang/translatedQuery) → optional preview back-translation | ✅ Code merged; E2E pending embedding quota recovery | mt-uni (query translation + preview back-translation) |
| ④ Live simultaneous interpretation | qwen3.8-livetranslate-flash-realtime WebSocket proxy (backend → Android/Web); 20.3s Chinese speech measured at 308 tokens (≈7 tokens/s) | ✅ Shipped | livetranslate (1M) |

**Design points of ③** (commit: `add-cross-language`):

- Translation happens **before vectorization**: only mt-uni is called, never the embedding quota; translation failure degrades to original-query retrieval without blocking the main path.
- Cache key stays the raw query (5-minute `rag.hits` cache); repeated foreign queries are further deduped by mt-uni's in-process cache.
- The retrieve-test trace gains `crossLanguage` and `backTranslateTookMs`; preview back-translation only handles ≤200-char previews across four lanes (vectorRecall/keywordRecall/rrfMerged/reranked), togglable via `rag.cross-language.translate-results`.
- Config: `rag.cross-language.enabled` (default on) / `target-lang` (default zh) / `translate-results` (default on).

## 6. Quota Boundaries and Next Steps

Per the current quota policy in `docs/platform/testing/model-usage-policy.md`:

- **Disabled**: `qwen3.7-text-embedding-flash` (ingestion, re-embedding, query vectorization all forbidden) and `qwen3.7-text-rerank` (quota exhausted). This report therefore contains **no measured cross-language retrieval metrics**; step ③'s verification boundary is "compiles + backend boots", with E2E deferred to quota recovery.
- **After quota recovery**:
  1. Build EN query sets (30–50 questions each on kb1/kb5) and run cross-language retrieval E2E, verifying "translate-to-Chinese ≥ direct foreign-query baseline";
  2. Calibrate a kb5-specific rerank threshold (see Finding 5);
  3. Expand colloquial parallel corpora and re-evaluate (see Finding 3);
  4. Regression-test live interpretation on a physical Android device.

## 7. Conclusion

The hybrid retrieval pipeline is production-usable on terminology queries at **Recall@5 0.96–0.975 / hit rate 96–97.5%**. The two biggest gaps are colloquial phrasing (0.167) and the absence of measured cross-language retrieval. Step ③ code is in place (translate → retrieve → back-translate, mt-uni only) and ready for closed-loop verification with English query sets once the embedding quota recovers.
