# -*- coding: utf-8 -*-
"""单次 kb4 检索计时诊断：定位慢在哪一步。"""

import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.normpath(os.path.join(HERE, "..", "rag-eval")))
sys.stdout.reconfigure(encoding="utf-8")

import eval_common  # noqa: E402

eval_common.REQUEST_TIMEOUT = 900

QUESTION = "Spring AI 的 Advisor 如何把检索到的知识注入对话？"


def main() -> None:
    token, csrf = eval_common.login()
    kbs = eval_common.api("GET", "/api/v1/knowledge", token=token, csrf=csrf)
    print("知识库:", [(k.get("id"), k.get("name"), k.get("chunkCount")) for k in kbs])

    started = time.time()
    trace = eval_common.api(
        "POST",
        "/api/v1/knowledge/4/retrieve-test",
        token=token,
        csrf=csrf,
        body={"query": QUESTION, "topK": 5},
    )
    elapsed = time.time() - started
    print(f"\n耗时 {elapsed:.1f}s")
    step_fields = [
        "rewriteTookMs", "vectorTookMs", "keywordTookMs", "parentTookMs",
        "graphTookMs", "mergeTookMs", "rerankTookMs",
    ]
    print("分步:", {f: trace.get(f) for f in step_fields})
    print("rerankStatus:", trace.get("rerankStatus"), trace.get("rerankReason"))
    for name in ("vectorRecall", "keywordRecall", "rrfMerged", "reranked"):
        entries = trace.get(name) or []
        print(f"{name}: {len(entries)} 条")
        for e in entries[:2]:
            text = " ".join(str(e.get("content") or e.get("text") or "").split())[:60]
            print(f"   score={e.get('rerankScore')} | {text}")


if __name__ == "__main__":
    main()
