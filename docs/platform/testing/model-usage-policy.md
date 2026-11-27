# 大模型调用授权清单

> 适用范围：本仓库内所有脚本、后端服务、评测工具与自动化任务。
> 生效时间：2026-11-27（因一次越权调用后建立）。

## 一、硬性规则

1. **未在本文档列出的任何大模型，一律不得调用**，包括但不限于 `deepseek-chat`、`qwen-plus`、本地 Ollama 模型（`qwen3:8b`、`qwen2.5-coder:7b`、`nomic-embed-text`）等。
2. 需要新增一个模型时，必须先向用户说明：**用途、预估调用次数、预估 token 消耗**，获得明确同意后才能写入本文档并调用。
3. 「临时试一下」「先跑通再说」不构成授权。
4. 任何脚本新增模型调用前，必须能在本文档找到对应条目，否则视为越权。

## 二、当前已授权的模型

| 模型 | 允许用途 | 额度（2026-11-27） |
|---|---|---|
| `qwen3.7-text-embedding-flash` | 知识库切片向量化、向量重嵌入 | 剩 217.33K / 1M |
| `qwen3.7-text-rerank` | 检索精排、评测链路 rerank 阶段 | 剩 502.22K / 1M |
| `qwen3.8-max-0902` | 生成 RAG 评测题（build_dataset 类任务） | 剩 1M / 1M |

## 三、越权事件记录

### 2026-11-27：qwen-plus 未经授权调用

- **经过**：生成 kb5 评测集时，后端 `/api/v1/model/ping` 因 `sys_model_config` 的 deepseek/qwen api_key 为空返回 401，本地 Ollama 生成一题需 116 秒。为提速，直接调用百炼 `qwen-plus` 的 `chat/completions` 接口生成 80 题。
- **调用量**：81 次（1 次探测 + 80 次生成），约 1.6 万 token。
- **性质**：用户只授权了两个向量模型，qwen-plus 属未授权模型，调用前未询问。
- **处置**：停止调用；相关脚本 `dev/rag-eval/build_dataset_kb5.py` 保留但标注为「使用前须确认授权模型」；后续改用 `qwen3.8-max-0902`。

## 四、脚本侧落点

| 脚本 | 使用的模型 | 备注 |
|---|---|---|
| `api/.../DashScopeEmbeddingModel.java` | qwen3.7-text-embedding-flash | 后端向量化 |
| `api/.../RerankService.java` | qwen3.7-text-rerank | 后端精排 |
| `dev/rag-eval/reembed.py` | qwen3.7-text-embedding-flash | 定点重嵌入 |
| `dev/rag-eval/probe_usage.py` | embedding + rerank | 单价实测 |
| `dev/rag-eval/build_dataset_kb5.py` | `--model` 参数决定，默认需人工确认 | 评测题生成 |
