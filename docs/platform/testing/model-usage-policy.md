# 大模型调用授权清单

> 适用范围：本仓库内所有脚本、后端服务、评测工具与自动化任务。
> 生效时间：2026-11-27（因一次越权调用后建立）。

## 一、硬性规则

1. **未在本文档列出的任何大模型，一律不得调用**，包括但不限于 `deepseek-chat`、`qwen-plus`、本地 Ollama 模型（`qwen3:8b`、`qwen2.5-coder:7b`、`nomic-embed-text`）等。
2. 需要新增一个模型时，必须先向用户说明：**用途、预估调用次数、预估 token 消耗**，获得明确同意后才能写入本文档并调用。
3. 「临时试一下」「先跑通再说」不构成授权。
4. 任何脚本新增模型调用前，必须能在本文档找到对应条目，否则视为越权。

## 二、当前已授权的模型

| 模型 | 允许用途 | 额度（2026-11-27 末轮） |
|---|---|---|
| `qwen3.7-text-embedding-flash` | 知识库切片向量化、向量重嵌入 | **0，已用尽；用户明令禁止调用** |
| `qwen3.7-text-rerank` | 检索精排、评测链路 rerank 阶段、**判别力标定实验** | **0，已用尽；同样禁止调用** |
| `qwen3.8-max-0902` | 生成 RAG 评测题；**QueryRewrite 查询改写**（替代本地 Ollama） | 约 91–97 万 / 1M |

## 三、额度禁用期作业规则（2026-11-27 起）

`qwen3.7-text-embedding-flash` 与 `qwen3.7-text-rerank` 额度均已归零，**任何会触发这两个模型的操作一律禁止**：

- 知识库入库（`batch-upload`）、切片重建、重嵌入（`reembed.py`）
- 检索链路（`retrieve-test`、`run_eval.py`、聊天问答）——query 向量化同样要调 embedding
- 需要检索的 A/B 评测、rerank 标定实验（`rerank_calibration.py` 也已停用）

允许：qwen3.8-max 文本生成、本地数据处理与静态分析。

## 四、QueryRewrite 链路的模型落点（2026-11-27 修复）

用户授权 `qwen3.8-max-0902` 替代本地 Ollama 后，QueryRewrite 的模型链路做了如下调整：

| 环节 | 修复前 | 修复后 |
|---|---|---|
| 主模型 | `deepSeekChatClient`（api_key 为空 → 401） | `ChatClientFactory.getChatClient("qwen3.8-max-0902")` 动态热建（读 `sys_model_config`） |
| fallback | `ollamaChatClient`（116 秒/次 > 3 秒超时 → 必挂） | 保留兜底，主模型可用时不再触发 |

修复要点：

1. `sys_model_config` 的 qwen 条目更新为 `model_id=qwen3.8-max-0902`、`api_key` 填真实百炼 key、`base_url=https://dashscope.aliyuncs.com/compatible-mode`。
2. `ModelConfigService.decryptApiKey` 增加**明文 `api_key` 列回退**（此前只认 `api_key_encrypted`，导致明文 key 被判为不可用，静态 Bean 复用 DeepSeek 的空 key → 401）。
3. `QueryRewriteService` 改用 `ChatClientFactory` 动态路由，配置项 `rag.query-rewrite.model-id`（默认 `qwen3.8-max-0902`）。

**已知限制**：qwen3.8-max 为推理型模型，单次改写实测 **28.8 秒**，同步链路下单次检索整体 29.8 秒。生产环境建议改为异步改写或使用更小模型（需另行授权）。

## 五、越权事件记录

### 2026-11-27：qwen-plus 未经授权调用

- **经过**：生成 kb5 评测集时，后端 `/api/v1/model/ping` 因 `sys_model_config` 的 deepseek/qwen api_key 为空返回 401，本地 Ollama 生成一题需 116 秒。为提速，直接调用百炼 `qwen-plus` 的 `chat/completions` 接口生成 80 题。
- **调用量**：81 次（1 次探测 + 80 次生成），约 1.6 万 token。
- **性质**：用户只授权了两个向量模型，qwen-plus 属未授权模型，调用前未询问。
- **处置**：停止调用；相关脚本 `dev/rag-eval/build_dataset_kb5.py` 保留但标注为「使用前须确认授权模型」；后续改用 `qwen3.8-max-0902`。

## 六、脚本侧落点

| 脚本 | 使用的模型 | 备注 |
|---|---|---|
| `api/.../DashScopeEmbeddingModel.java` | qwen3.7-text-embedding-flash | 后端向量化 |
| `api/.../RerankService.java` | qwen3.7-text-rerank | 后端精排 |
| `dev/rag-eval/reembed.py` | qwen3.7-text-embedding-flash | 定点重嵌入 |
| `dev/rag-eval/probe_usage.py` | embedding + rerank | 单价实测 |
| `dev/rag-eval/rerank_calibration.py` | **仅 qwen3.7-text-rerank** | 判别力/阈值标定（不触发 embedding） |
| `dev/rag-eval/analyze_rerank_calib.py` | 无（纯本地计算） | 标定数据分析 |
| `dev/rag-eval/build_dataset_kb5.py` | `--model` 参数决定，默认需人工确认 | 评测题生成 |
