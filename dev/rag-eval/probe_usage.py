# -*- coding: utf-8 -*-
"""实测百炼 embedding / rerank 单次 token 成本，用于额度排期。"""

import json
import os
import sys
import urllib.request

HOST = "https://dashscope.aliyuncs.com"
KEY = os.environ.get("DASHSCOPE_API_KEY") or ""
if not KEY:
    sys.exit("缺少 DASHSCOPE_API_KEY")
sys.stdout.reconfigure(encoding="utf-8")

BASE = "数据库索引通过B树组织数据页，使范围查询与等值查询都能在对数时间内完成，代价是写入放大与页分裂。"
TEXT = (BASE * 40)[:1200]
assert len(TEXT) == 1200, len(TEXT)


def post(path: str, body: dict) -> dict:
    request = urllib.request.Request(
        HOST + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    # 1) rerank：10 条候选 × 1200 字符（当前线上配置）
    rerank = post(
        "/api/v1/services/rerank/text-rerank/text-rerank",
        {
            "model": "qwen3.7-text-rerank",
            "input": {"query": "数据库索引为什么能加速查询", "documents": [TEXT] * 10},
            "parameters": {"top_n": 5, "return_documents": False},
        },
    )
    print("rerank usage:", json.dumps(rerank.get("usage"), ensure_ascii=False))
    print("rerank results[0]:", (rerank.get("output") or {}).get("results", [{}])[0])

    # 2) rerank：6 条 × 800 字符（建议的降本配置）
    short = (BASE * 27)[:800]
    rerank2 = post(
        "/api/v1/services/rerank/text-rerank/text-rerank",
        {
            "model": "qwen3.7-text-rerank",
            "input": {"query": "数据库索引为什么能加速查询", "documents": [short] * 6},
            "parameters": {"top_n": 5, "return_documents": False},
        },
    )
    print("rerank(slim) usage:", json.dumps(rerank2.get("usage"), ensure_ascii=False))

    # 2b) rerank：10 条 × 800 字符（当前线上配置）
    rerank2b = post(
        "/api/v1/services/rerank/text-rerank/text-rerank",
        {
            "model": "qwen3.7-text-rerank",
            "input": {"query": "数据库索引为什么能加速查询", "documents": [short] * 10},
            "parameters": {"top_n": 5, "return_documents": False},
        },
    )
    print("rerank(10x800) usage:", json.dumps(rerank2b.get("usage"), ensure_ascii=False))

    # 3) embedding：1 条 1200 字符
    emb = post(
        "/compatible-mode/v1/embeddings",
        {
            "model": "qwen3.7-text-embedding-flash",
            "input": [TEXT],
            "dimensions": 768,
            "encoding_format": "float",
        },
    )
    print("embedding usage:", json.dumps(emb.get("usage"), ensure_ascii=False), "chars=", len(TEXT))


if __name__ == "__main__":
    main()
