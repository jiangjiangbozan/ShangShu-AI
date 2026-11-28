# 语料增强报告（摘要 + 关键词 + 问答对）

> 时间：2026-11-27 · 模型：`qwen3.8-max-0902` · 覆盖 255 篇文档 / 3,179 条切片
> 用途：在 embedding / rerank 额度归零的空窗期，把剩余的大语言额度转成**结构化语料资产**。

## 1. 概览

| 知识库 | 文档数 | 平均 token/篇 | 小计 |
|---|---|---|---|
| kb1 测试知识库 | 2 | 2,978 | 5,956 |
| kb3 技术文档库 | 27 | 2,702 | 59,098（含试跑 3 篇） |
| kb4 官方技术文档库 | 33 | 2,228 | 73,540 |
| kb5 中文技术文档库 | 193 | 2,624 | 532,186 |
| **合计** | **255** | **≈2,630** | **671,725** |

产出：**255 条摘要、1,275 个关键词、765 组问答对**。

产物：`dev/rag-corpus/enrich/enrich-kb{1,3,4,5}.jsonl`（共 268 KB），每行一篇：

```json
{"docId": 29, "kbId": 3, "title": "vue3.md",
 "summary": "该文档介绍基于 Vue3.5、TypeScript、Vite8 和 Element Plus 的前端项目规范……",
 "keywords": ["Vue3", "TypeScript", "Vite", "Element Plus", "组件规范"],
 "qa": [{"q": "项目前端框架包含哪些技术？", "a": "Vue 3.5、TypeScript、Vite 8 和 Element Plus 2.x。"}, ...],
 "usage": {"prompt_tokens": 2142, "completion_tokens": 352, "total_tokens": 2494},
 "chars": 3000}
```

## 2. 质量抽样

| 文档 | 摘要（截断） | 问答对示例 |
|---|---|---|
| `vue3.md` | 基于 Vue3.5/TS/Vite8/Element Plus 的前端项目规范，说明目录结构、script setup 写法、路径别名与 Vite 代理 | Q：路径别名 @ 被配置为什么？A：指向 ./src |
| `spring-ai.md` | Spring AI 2.0 核心概念、依赖与配置，涵盖 ChatClient、Advisor、Redis 会话记忆、ES 向量存储及多模型接入 | Q：ChatMemory 已配置为什么实现？A：Redis 实现，键前缀 `ai:chat:memory:` |
| `01-concepts-windows-intro.md` | K8s 中 Windows 容器的节点要求、兼容限制和 Pod 能力 | Q：Windows 节点要求？A：…… |

共 765 组问答对，问题均要求"能在片段中找到依据"，答案简明。

## 3. 资产用途

1. **前端文档卡片**：知识库列表可直接展示摘要与关键词，不用每次调 LLM 重新生成；
2. **评测题冷启动**：765 组问答对本身可作 RAG 评测集（题干来自原文，答案可校验），后续按需抽样进去重、改写流程；
3. **检索 query 扩展**：每篇 5 个关键词可作为同义词/扩展词典，弥补当前 QueryRewrite 单次 28.8 秒的延迟问题；
4. **微调数据**：QA 对 + 原文可作为本地小模型的 SFT/蒸馏语料。

## 4. 接入建议（待确认后执行）

`knowledge_document` 表当前无摘要字段。两种落地方式：

- **方案 A（推荐）**：`ALTER TABLE knowledge_document ADD COLUMN summary VARCHAR(512) NULL, ADD COLUMN keywords VARCHAR(256) NULL, ADD COLUMN qa_json TEXT NULL;`，再用一次性脚本回填；前端文档列表直接读；
- **方案 B**：新建 `knowledge_document_enrich` 表（docId 关联），避免改主表结构。

两种都不消耗任何模型额度（数据已在 JSONL 里）。

## 5. 复现

```powershell
cd dev/rag-corpus
python enrich_corpus.py --kb 3 --concurrency 3 --budget 100000   # 单库
python enrich_corpus.py --kb 5 --concurrency 3 --budget 520000
```

脚本支持断点续跑（读取已有 JSONL 的 docId 跳过），`--budget` 控制累计 token 上限，达到即停止提交新任务。

## 6. 消耗与余额

本轮 usage 合计 **671,725 token**（qwen3.8-max-0902）。按本轮起点 999.88K 估算，剩余约 32 万（此前 QueryRewrite 30 次改写另有约 3–9 万消耗，实际以控制台为准）。
