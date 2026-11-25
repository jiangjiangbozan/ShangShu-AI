# 工作流节点使用文档（20 种）

> 契约与 [WorkflowEngine](../api/src/main/java/com/ai/api/workflow/engine/WorkflowEngine.java) 及 [engine/node/](../api/src/main/java/com/ai/api/workflow/engine/node/) 下 20 个 `NodeExecutor` 一一对应。
> 节点间传参语法：`${nodeId.outputKey}` 引用上游输出，`${global.key}` 引用工作流全局输入。整个字符串为一个 `${...}` 时保留原始类型，否则按字符串拼接。

## DSL 结构速览

```json
{
  "version": "1.0",
  "viewport": { "x": 0, "y": 0, "zoom": 1 },
  "nodes": [
    { "id": "node-1", "type": "start", "position": { "x": 100, "y": 200 }, "data": { } }
  ],
  "edges": [
    { "source": "node-1", "target": "node-2", "sourceHandle": "if" }
  ]
}
```

- 引擎只读 `nodes[].id/type/data` 与 `edges[].source/target/sourceHandle`；`position`/`viewport` 供编辑器渲染
- `sourceHandle` 为空（或缺失）的边**总是执行**；有值时仅当上游输出分支与之匹配才执行（如 `if-else` 的 `if`/`else`）
- 官方模板见 [docs/workflow-templates/](workflow-templates/)

---

## 1. start — 开始

工作流入口，总是执行。输出 = 运行时传入的 inputs，并为未传变量填充默认值。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `variables` | `[{variable, default}]` | 入参声明：`variable` 为变量名，运行时 inputs 缺失时取 `default` |

输出：`variables` 中声明的每个变量名（值取运行时 inputs 或默认值）。
常见错误：inputs 传入了未声明的变量名——不会报错，但下游 `${start.xxx}` 引用前请先声明。

## 2. end — 结束

终结节点，无配置、无输出。工作流以 End 节点输出（由 AI 应用 workflow 类型调用时取 End 前的 answer/text）收尾；并行分支未汇入 End 时流程仍以最后一批节点完成即结束。

## 3. llm — LLM 调用

调用大模型生成文本。prompt 中 `${nodeId.outputKey}` 先由变量池替换。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `model` | string | `deepseek-chat` | 模型别名（sys_model_config 中的标识） |
| `promptTemplate` | string | — | Prompt 模板，支持 `${var}` 与 `${global.var}` |
| `temperature` | number | `0.7` | 采样温度 |
| `maxTokens` | int | `2048` | 最大生成 token |

输出：`text`（生成文本）、`tokens`（消耗 token 数）。
常见错误：模型别名不存在 → 执行失败；prompt 未引用上游输出 → 每次生成相同内容。

## 4. if-else — 条件分支

按条件列表顺序求值，**第一个为真**走 `if` 分支，否则走 `else`。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `conditions` | `[{variableSelector, comparisonOperator, value}]` | 条件列表 |

`variableSelector`：`{nodeId, outputKey}` 指定取哪个节点的哪个输出；`comparisonOperator` 支持 `=`、`!=`、`>`、`<`、`contains` 等。

输出：`branch`（`if` / `else`）；出边用 `sourceHandle: "if"` / `"else"` 接下游。
常见错误：`variableSelector.nodeId` 写成节点名称而非节点 id → 取值失败按假处理。

## 5. loop — 循环判断

控制循环体是否继续，配合 `iteration` 计数实现 while 循环。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `loopCondition` | `{variableSelector}` | — | 求值的条件变量 |
| `maxIterations` | int | `100` | 最大迭代次数（防死循环） |

输出：`continueLoop`（boolean）、`iteration`（当前轮次）。
常见错误：循环体内不更新条件变量 → 打满 `maxIterations` 后终止。

## 6. iteration — 迭代器

把上游列表变量展开为待遍历项。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `iterator` | `{nodeId, outputKey}` | 上游输出变量选择器 |

输出：`items`（数组，非列表值包装为单项数组）、`length`。
常见错误：上游输出不是数组 → 值被包装成单元素数组继续执行（不报错）。

## 7. knowledge-retrieval — 知识检索

调用知识库 RAG 检索链路（多路召回 + RRF + Rerank）。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `knowledgeBaseId` | string | — | 知识库 ID（必填） |
| `query` | `{nodeId, outputKey}` | — | 查询文本选择器 |
| `topK` | int | `5` | 返回条数 |
| `scoreThreshold` | number | `0.5` | 分数阈值 |

输出：`results`（chunk 列表）、`count`、`query`（实际查询文本）。
常见错误：`knowledgeBaseId` 不存在或为空 → 返回空 results。

## 8. question-classifier — 问题分类

用 LLM 把问题归入预定义类别。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `model` | string | 分类用模型 |
| `query` | `{nodeId, outputKey}` | 待分类文本选择器 |
| `categories` | `[{id/name, ...}]` | 类别清单 |

输出：`class_id`、`class_name`、`query`。下游 `if-else` 用 `class_name` 的 `contains`/`=` 判断分支。
常见错误：LLM 返回的类别名与 categories 定义不一致 → `class_name` 落为空。

## 9. agent — Agent 执行

运行 Agent 引擎（ReAct / Function Calling），自主多轮调用工具。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `model` | string | `deepseek-chat` | 模型别名 |
| `promptTemplate` | string | — | 任务指令模板 |
| `tools` | `[string]` | `[]` | 注册的工具名列表 |
| `maxIterations` | int | `10` | 最大推理轮次 |

输出：`text`（最终回答）、`tokens`、`maxIterations`。

## 10. code — 代码执行

在沙箱中执行脚本（默认 JavaScript），适合数据变换。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `codeLanguage` | string | `javascript` | 脚本语言 |
| `code` | string | — | 脚本源码 |
| `inputs` | `{变量名: value}` | `{}` | 注入脚本的变量映射（值支持 `${var}`） |

输出：脚本返回的 Map 的**全部键**展开到输出。
常见错误：脚本抛异常 → 节点失败（配合节点级 `retry`/`onError` 使用）。

## 11. http-request — HTTP 请求

调用外部 HTTP API。url 支持 `${var}` 插值。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `method` | string | `GET` | HTTP 方法 |
| `url` | string | — | 请求地址，支持 `${var}` |
| `headers` | `{k: v}` | `{}` | 请求头 |
| `body` | string | — | 请求体（模板字符串） |
| `timeout` | int | `30000` | 超时毫秒 |

输出：`statusCode`、`headers`、`body`。

## 12. tool — 工具调用

调用平台已注册的单个工具（不走 Agent 推理循环）。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `toolName` | string | 工具名（必填） |
| `inputs` | `{k: v}` | 工具入参（值支持 `${var}`） |

输出：`result`，且结果 Map 的键展开到输出。

## 13. human-input — 人工输入

挂起流程等待人工补充字段（表单由 `inputForm` 声明）。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `inputForm` | `[{...字段定义}]` | 人工填写的字段清单 |

输出：`outputs`（字段值 Map），且每个字段名展开到输出。

## 14. variable-assigner — 变量赋值

把上游输出复制为指定名称的变量。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `assignments` | `[{variable, valueSelector: {nodeId, outputKey}}]` | 赋值列表 |

输出：每个 `variable` 名称对应的键。

## 15. variable-aggregator — 变量聚合

收集多个上游输出（典型：并行分支汇合）。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `variables` | `[{nodeId, outputKey}]` | 聚合变量选择器列表 |

输出：`items`（非空值列表）、`length`。

## 16. template-transform — 模板转换

字符串模板拼接，输出单一文本。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `template` | string | 模板文本，`${nodeId.key}` 替换 |

输出：`output`（拼接后的字符串）。

## 17. parameter-extractor — 参数抽取

用 LLM 从文本中按 schema 抽取结构化参数。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `model` | string | 抽取用模型 |
| `query` | `{nodeId, outputKey}` | 源文本选择器 |
| `parameters` | `[{name, ...}]` | 参数定义 |
| `instruction` | string | 抽取说明 |

输出：`parameters`（抽取结果 Map），且每个参数名展开到输出。

## 18. list-operator — 列表过滤

对列表变量做条件过滤。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `input` | `{nodeId, outputKey}` | 列表变量选择器 |
| `filterCondition` | `{...}` | 过滤条件 |

输出：`result`（过滤后的列表）、`length`。

## 19. document-extractor — 文档抽取

从上游变量（文件引用/文本）提取内容。

| 配置键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `input` | `{nodeId, outputKey}` | — | 输入选择器 |
| `outputType` | string | `text` | `text` / `markdown` |

输出：`text`、`markdown`（仅 outputType=markdown 时有值）、`outputType`。

## 20. answer — 直接回复

工作流最终回复节点。

| 配置键 | 类型 | 说明 |
|---|---|---|
| `answer` | `{nodeId, outputKey}` | 取哪个上游输出作为回复 |

输出：`answer`（字符串）。

---

## 节点级重试与容错

所有节点支持在 `data` 中配置（Phase 3 引入）：

```json
{ "id": "node-3", "type": "llm",
  "data": {
    "retry": { "maxAttempts": 3, "backoffMs": 500 },
    "onError": "continue"
  } }
```

- `retry.maxAttempts`：失败重试次数（默认 1 = 不重试）
- `retry.backoffMs`：重试间隔毫秒（默认 0）
- `onError`：`fail`（默认，中断流程）/ `continue`（记 failed 状态，流程继续）

## 第三方扩展

自定义节点见 [workflow-node-spi.md](workflow-node-spi.md)：实现 `NodeExecutor` + 注册 Bean 即自动并入引擎，无需修改引擎代码。
