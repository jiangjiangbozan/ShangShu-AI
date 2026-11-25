# -*- coding: utf-8 -*-
"""执行 RAG 检索链路评测：逐题调用 retrieve-test，按链路阶段（纯向量/纯关键词/RRF/Rerank）计算
Recall@5、MRR、nDCG@5 与耗时，输出 results.json 与 docs/rag-eval-report.md。"""

import argparse
import json
import math
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import eval_common

SHANGHAI = ZoneInfo("Asia/Shanghai")
TOP_K = 5

# 评测阶段：报告行名 -> trace 字段
STAGES = [
    ("纯向量", "vectorRecall"),
    ("纯关键词", "keywordRecall"),
    ("向量+关键词+RRF", "rrfMerged"),
    ("+Rerank", "reranked"),
]

STEP_FIELDS = [
    "rewriteTookMs",
    "vectorRecallTookMs",
    "keywordRecallTookMs",
    "rrfMergedTookMs",
    "rerankTookMs",
]


def as_int(value):
    """耗时字段转 int（服务端可能返回字符串数字，非法值按 0 处理）。"""
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def chunk_ids(trace, field):
    """提取某阶段命中的 chunkId 列表（保持服务端排序）。"""
    entries = trace.get(field) or []
    result = []
    for entry in entries:
        if isinstance(entry, dict) and entry.get("chunkId") is not None:
            result.append(str(entry["chunkId"]))
    return result


def recall_at_k(ranked, relevant, k=TOP_K):
    """top-k 命中的标准 chunk 占全部标准 chunk 的比例。"""
    if not relevant:
        return 0.0
    hits = len(set(ranked[:k]) & set(relevant))
    return hits / len(relevant)


def mrr(ranked, relevant, k=TOP_K):
    """第一个标准 chunk 排名倒数的均值（本题内单值）。"""
    for i, cid in enumerate(ranked[:k]):
        if cid in relevant:
            return 1.0 / (i + 1)
    return 0.0


def ndcg_at_k(ranked, relevant, k=TOP_K):
    """二值相关度 nDCG@k。"""
    if not relevant:
        return 0.0
    dcg = sum(1.0 / math.log2(i + 2) for i, cid in enumerate(ranked[:k]) if cid in relevant)
    ideal_len = min(len(relevant), k)
    idcg = sum(1.0 / math.log2(i + 2) for i in range(ideal_len))
    return dcg / idcg if idcg > 0 else 0.0


def percentile(values, p):
    """线性插值分位数（p 取 0.50/0.95）。"""
    if not values:
        return 0
    ordered = sorted(values)
    idx = (len(ordered) - 1) * p
    lo, hi = int(idx), min(int(idx) + 1, len(ordered) - 1)
    frac = idx - lo
    return ordered[lo] + (ordered[hi] - ordered[lo]) * frac


def evaluate(token, csrf, kb_id, items, top_k, rrf_k=None):
    """逐题评测，返回 (逐题结果列表, 阶段聚合指标, 耗时列表, rerank 状态分布)。"""
    per_query = []
    stage_metrics = {name: {"recall": [], "mrr": [], "ndcg": [], "hit": 0} for name, _ in STAGES}
    latencies = []
    rerank_status = {"success": 0, "disabled": 0, "degraded": 0, "missing": 0}

    for idx, item in enumerate(items, 1):
        question = item["question"]
        relevant = [str(c) for c in item.get("relevantChunkIds", [])]
        body = {"query": question, "topK": top_k}
        if rrf_k:
            body["rrfK"] = rrf_k
        try:
            trace, wall_ms = eval_common.timed(
                lambda: eval_common.api(
                    "POST", f"/api/v1/knowledge/{kb_id}/retrieve-test",
                    token=token, csrf=csrf, body=body)
            )
        except RuntimeError as e:
            print(f"[{idx}/{len(items)}] 失败: {e}")
            continue

        server_ms = sum(as_int(trace.get(field, 0)) for field in STEP_FIELDS)
        latencies.append({"serverMs": server_ms, "wallMs": wall_ms})
        status = trace.get("rerankStatus") or ("missing" if trace.get("rerankTookMs") is None else "missing")
        rerank_status[status if status in rerank_status else "missing"] += 1

        row = {"id": item["id"], "question": question, "relevant": relevant,
               "serverMs": server_ms, "wallMs": wall_ms,
               "rerankStatus": status,
               "rrfK": trace.get("rrfK") or rrf_k}  # 耗时/rerank 状态入结果，报告可离线重渲染
        for name, field in STAGES:
            ranked = chunk_ids(trace, field)
            r = recall_at_k(ranked, relevant)
            m = mrr(ranked, relevant)
            n = ndcg_at_k(ranked, relevant)
            stage_metrics[name]["recall"].append(r)
            stage_metrics[name]["mrr"].append(m)
            stage_metrics[name]["ndcg"].append(n)
            stage_metrics[name]["hit"] += 1 if m > 0 else 0
            row[field] = {"ranked": ranked[:top_k], "recall": round(r, 4),
                          "mrr": round(m, 4), "ndcg": round(n, 4)}
        per_query.append(row)
        print(f"[{idx}/{len(items)}] {question[:40]} -> rerank nDCG={row['reranked']['ndcg']:.2f}")

    summary = {}
    for name, _ in STAGES:
        metrics = stage_metrics[name]
        count = max(len(metrics["recall"]), 1)
        summary[name] = {
            "recall@k": sum(metrics["recall"]) / count,
            "mrr": sum(metrics["mrr"]) / count,
            "ndcg@k": sum(metrics["ndcg"]) / count,
            "hitRate": metrics["hit"] / count,
        }
    return per_query, summary, latencies, rerank_status


def text_bar(value, width=30):
    """无 matplotlib 时的文本柱状图。"""
    filled = round(value * width)
    return "█" * filled + "░" * (width - filled) + f" {value:.3f}"


def render_chart(summary, report_dir):
    """有 matplotlib 时输出 PNG 柱状图，返回相对引用路径；否则返回 None 走文本柱。"""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "sans-serif"]
        names = [name for name, _ in STAGES]
        metrics = [("recall@k", "Recall@5"), ("mrr", "MRR"), ("ndcg@k", "nDCG@5")]
        x = range(len(names))
        width = 0.25
        fig, ax = plt.subplots(figsize=(8, 4.5))
        for i, (key, label) in enumerate(metrics):
            values = [summary[name][key] for name in names]
            ax.bar([xi + (i - 1) * width for xi in x], values, width, label=label)
        ax.set_xticks(list(x))
        ax.set_xticklabels(names)
        ax.set_ylim(0, 1)
        ax.set_title("RAG 检索链路分阶段指标对比")
        ax.legend()
        fig.tight_layout()
        chart_path = report_dir / "rag-eval-chart.png"
        fig.savefig(chart_path, dpi=120)
        plt.close(fig)
        return chart_path.name
    except Exception as e:  # noqa: BLE001 - matplotlib 缺失/中文字体缺失时降级为文本柱
        print(f"matplotlib 不可用（{e}），使用文本柱状图")
        return None


def render_report(args, items, summary, latencies, eval_count, rerank_status):
    """生成 Markdown 报告。"""
    avg_server = sum(item["serverMs"] for item in latencies) / max(len(latencies), 1)
    avg_wall = sum(item["wallMs"] for item in latencies) / max(len(latencies), 1)
    p50_server = percentile([i["serverMs"] for i in latencies], 0.50)
    p95_server = percentile([i["serverMs"] for i in latencies], 0.95)
    p50_wall = percentile([i["wallMs"] for i in latencies], 0.50)
    p95_wall = percentile([i["wallMs"] for i in latencies], 0.95)
    now = datetime.now(SHANGHAI).strftime("%Y-%m-%d %H:%M")
    lines = [
        "# RAG 检索链路评测报告",
        "",
        f"> 生成时间：{now}（Asia/Shanghai）  ",
        f"> 知识库：`{args.kb_id}` · 数据集：`{eval_count}/{len(items)}` 题 · topK：{args.top_k} · "
        f"RRF k：{args.rrf_k or '默认(60)'} · "
        f"接口：`POST /api/v1/knowledge/{{kbId}}/retrieve-test`（不走缓存）",
        "",
        "## 指标总览",
        "",
        "| 阶段 | Recall@5 | MRR | nDCG@5 | 命中率 |",
        "|---|---|---|---|---|",
    ]
    for name, _ in STAGES:
        s = summary[name]
        lines.append(
            f"| {name} | {s['recall@k']:.3f} | {s['mrr']:.3f} | {s['ndcg@k']:.3f} | {s['hitRate']:.1%} |"
        )
    lines += [
        "",
        f"平均服务端链路耗时 **{avg_server:.0f} ms**（QueryRewrite+双路召回+RRF+Rerank 各步之和），"
        f"客户端整体耗时 **{avg_wall:.0f} ms**。",
        "",
        "## 延迟分布",
        "",
        "| 指标 | P50 | P95 | 平均 |",
        "|---|---|---|---|",
        f"| 服务端链路 (ms) | {p50_server:.0f} | {p95_server:.0f} | {avg_server:.0f} |",
        f"| 客户端整体 (ms) | {p50_wall:.0f} | {p95_wall:.0f} | {avg_wall:.0f} |",
        "",
        "## Rerank 调用状态",
        "",
        f"`success` {rerank_status['success']} 次 · `disabled` {rerank_status['disabled']} 次 · "
        f"`degraded` {rerank_status['degraded']} 次 · `missing` {rerank_status['missing']} 次",
        "",
        "> `disabled`：`rag.rerank.enabled=false` 或未配置 `rag.rerank.base-url`（RRF 原序直出）；"
        "`degraded`：rerank 服务调用/解析失败自动降级。status 与降级原因由 retrieve-test trace 的"
        " `rerankStatus`/`rerankReason` 字段透出。",
        "",
        "## 柱状图",
        "",
    ]
    chart = render_chart(summary, args.report.parent)
    if chart:
        lines += [f"![指标对比]({chart})", ""]
    else:
        lines += ["### Recall@5", ""]
        for name, _ in STAGES:
            lines.append(f"- {name}：`{text_bar(summary[name]['recall@k'])}`")
        lines += ["", "### MRR", ""]
        for name, _ in STAGES:
            lines.append(f"- {name}：`{text_bar(summary[name]['mrr'])}`")
        lines += ["", "### nDCG@5", ""]
        for name, _ in STAGES:
            lines.append(f"- {name}：`{text_bar(summary[name]['ndcg@k'])}`")
        lines.append("")
    lines += [
        "## 结论要点",
        "",
    ]
    vec, rer = summary["纯向量"], summary["+Rerank"]
    rerank_degraded = all(abs(vec[k] - rer[k]) < 1e-6 for k in vec)
    if rerank_degraded:
        lines.append(
            "- **Rerank 精排未生效**：+Rerank 与纯向量指标完全一致，说明本次评测期间精排处于降级/关闭状态"
            "（`rag.rerank.enabled=false` 或 rerank 模型不可用），trace 的 reranked 为 RRF 候选原序。"
            "启用 rerank（配置 `RAG_RERANK_*`）后重跑即可对比精排增益。"
        )
    else:
        lines.append(
            "- Rerank 阶段相对 RRF 融合阶段的指标差值即精排带来的排序增益；"
            "若为负需检查 rerank 模型配置。"
        )
    lines += [
        "- 纯向量与纯关键词的召回互补性体现在 RRF 阶段相对两单路阶段的提升幅度。",
        "- **Token 消耗**：检索链路 trace 未透出 token 计数（QueryRewrite 的 LLM 调用计入服务端 sys_llm_log），"
        "本报告暂无法统计，后续可在 retrieveWithTrace 中补充透出。",
        "",
        "## 复现方式",
        "",
        "```bash",
        "cd dev/rag-eval",
        f"python build_dataset.py --kb-id {args.kb_id} --count {len(items)}",
        f"python run_eval.py --kb-id {args.kb_id} --top-k {args.top_k}",
        "```",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="RAG 检索链路评测")
    parser.add_argument("--kb-id", required=True, help="知识库 ID")
    parser.add_argument("--dataset", default="dataset.jsonl", help="数据集文件")
    parser.add_argument("--top-k", type=int, default=5, help="topK（默认 5）")
    parser.add_argument("--rrf-k", type=int, default=None,
                        help="RRF 融合参数 k 覆盖（评测对比用，默认走服务端配置 60）")
    parser.add_argument("--output", default="results.json", help="逐题结果输出")
    parser.add_argument("--report", default="../../docs/rag-eval-report.md", help="报告输出路径")
    args = parser.parse_args()
    # Path 化：render_chart/render_report 依赖 .parent 拼图路径，argparse 默认值是 str
    args.report = Path(args.report)

    with open(args.dataset, encoding="utf-8") as f:
        items = [json.loads(line) for line in f if line.strip()]
    print(f"数据集 {len(items)} 题，开始评测")

    token, csrf = eval_common.login()
    per_query, summary, latencies, rerank_status = evaluate(
        token, csrf, args.kb_id, items, args.top_k, args.rrf_k)

    Path(args.output).write_text(
        json.dumps({"summary": summary, "perQuery": per_query,
                    "rerankStatus": rerank_status,
                    "latency": {"p50ServerMs": percentile([i["serverMs"] for i in latencies], 0.50),
                                "p95ServerMs": percentile([i["serverMs"] for i in latencies], 0.95),
                                "p50WallMs": percentile([i["wallMs"] for i in latencies], 0.50),
                                "p95WallMs": percentile([i["wallMs"] for i in latencies], 0.95)}},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    report_path = Path(args.report).resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        render_report(args, items, summary, latencies, len(per_query), rerank_status),
        encoding="utf-8")
    print(f"完成：{args.output} + {report_path}")


if __name__ == "__main__":
    main()
