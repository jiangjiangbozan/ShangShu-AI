# -*- coding: utf-8 -*-
"""验证「官方技术文档库」(kb=4) 的检索可用性：3 个跨技术栈问题，看 top3 命中与 rerank 分数。"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.normpath(os.path.join(HERE, "..", "rag-eval")))
sys.stdout.reconfigure(encoding="utf-8")

import eval_common  # noqa: E402

QUESTIONS = [
    "Spring AI 的 Advisor 如何把检索到的知识注入对话？",
    "MyBatis-Plus 分页插件怎么配置和使用？",
    "RabbitMQ 消费者预取 prefetch 如何限制未确认消息数量？",
]


def main() -> None:
    token, csrf = eval_common.login()
    for question in QUESTIONS:
        trace = eval_common.api(
            "POST",
            "/api/v1/knowledge/4/retrieve-test",
            token=token,
            csrf=csrf,
            body={"query": question, "topK": 5},
        )
        print(f"\nQ: {question}")
        print(
            f"  rerankStatus={trace.get('rerankStatus')} tookMs={trace.get('rerankTookMs')} "
            f"vector={len(trace.get('vectorRecall') or [])} keyword={len(trace.get('keywordRecall') or [])} "
            f"rrf={len(trace.get('rrfMerged') or [])} reranked={len(trace.get('reranked') or [])}"
        )
        ranked = trace.get("reranked") or []
        if ranked:
            print("  entry keys:", list(ranked[0].keys()))
        for entry in ranked[:3]:
            text = entry.get("content") or entry.get("text") or entry.get("snippet") or ""
            text = " ".join(str(text).split())[:70]
            print(f"  - score={entry.get('rerankScore')} final={entry.get('finalScore')} | {text}")


if __name__ == "__main__":
    main()
