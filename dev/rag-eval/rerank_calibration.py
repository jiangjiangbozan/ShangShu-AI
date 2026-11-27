# -*- coding: utf-8 -*-
"""rerank 判别力标定数据集（只调用 qwen3.7-text-rerank）

背景：embedding 额度已清零并禁用，本脚本刻意不触碰任何 embedding 接口，
数据全部来自本地 dataset-kb5.jsonl（每题自带 sourceChunkExcerpt 正例文本）。

任务：每题 = 1 个正例片段 + 4 个来自其他文档的干扰片段，让 rerank 打分，
产出「中文技术领域 cross-encoder 判别数据」+ 正负例分数分布，用于：
  1) 标定 rerank 门控阈值（当前 0.5 是否合理）
  2) 未来本地 rerank 模型微调的训练/验证数据

用法：
  python rerank_calibration.py --probe        # 只跑 1 题，打印原始响应（看 usage 字段）
  python rerank_calibration.py --n 30         # 跑前 30 题
"""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
import time

import requests

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
DATASET = os.path.join(HERE, "dataset-kb5.jsonl")
URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
MODEL = "qwen3.7-text-rerank"
MAX_CHARS = 800   # 与线上 max-candidate-chars 一致，保证分数可比
NEG_PER_Q = 4
TOP_N = 5


def load_key() -> str:
    for cand in (
        os.path.join(HERE, "..", "..", "api", "src", "main", "resources", "application-secret.yml"),
        os.path.join(HERE, "..", "..", "api", "src", "main", "resources", "application-dev.yml"),
    ):
        if os.path.exists(cand):
            for line in open(cand, encoding="utf-8"):
                s = line.strip()
                if s.startswith("api-key:"):
                    return s.split(":", 1)[1].strip().strip('"').strip("'")
    env = os.environ.get("DASHSCOPE_API_KEY")
    if env:
        return env
    raise SystemExit("未找到 dashscope api-key")


def rerank(query: str, documents: list[str], top_n: int) -> dict:
    body = {
        "model": MODEL,
        "input": {"query": query, "documents": documents},
        "parameters": {"top_n": top_n, "return_documents": False},
    }
    resp = requests.post(
        URL,
        headers={"Authorization": f"Bearer {load_key()}", "Content-Type": "application/json"},
        json=body,
        timeout=90,
    )
    resp.raise_for_status()
    return resp.json()


def section_of(item: dict) -> str:
    """从 note 文件名取分区（如 176-concepts-scheduling-eviction-... → concepts）。"""
    note = item.get("note") or ""
    parts = note.split("-")
    return parts[1] if len(parts) > 1 else ""


def build_pair(items: list[dict], target: dict, rng: random.Random, neg: int, hard: bool) -> list[dict]:
    pos = target["sourceChunkExcerpt"][:MAX_CHARS]
    others = [x for x in items if x["id"] != target["id"] and x["sourceDocId"] != target["sourceDocId"]]
    if hard:
        sec = section_of(target)
        same = [x for x in others if section_of(x) == sec]
        if len(same) >= neg:
            negs = rng.sample(same, neg)
        else:
            negs = same + rng.sample([x for x in others if x not in same], neg - len(same))
    else:
        negs = rng.sample(others, neg)
    docs = [pos] + [n["sourceChunkExcerpt"][:MAX_CHARS] for n in negs]
    return [
        {"chunkId": (target.get("relevantChunkIds") or [""])[0], "label": 1,
         "docId": target["sourceDocId"], "note": target["note"], "text": pos},
        *[
            {"chunkId": (n.get("relevantChunkIds") or [""])[0], "label": 0,
             "docId": n["sourceDocId"], "note": n["note"], "text": n["sourceChunkExcerpt"][:MAX_CHARS]}
            for n in negs
        ],
    ]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--probe", action="store_true", help="只跑 1 题并打印原始响应")
    ap.add_argument("--n", type=int, default=30, help="题数（前 N 题）")
    ap.add_argument("--neg", type=int, default=NEG_PER_Q, help="每题干扰项数量（正例之外）")
    ap.add_argument("--hard", action="store_true", help="同分区（concepts/tasks 等）采样负例，提高难度")
    ap.add_argument("--seed", type=int, default=42, help="负例采样种子")
    args = ap.parse_args()

    items = [json.loads(line) for line in open(DATASET, encoding="utf-8") if line.strip()]
    rng = random.Random(args.seed)
    top_n = args.neg + 1

    if args.probe:
        cands = build_pair(items, items[0], rng, args.neg, args.hard)
        data = rerank(items[0]["question"], [c["text"] for c in cands], top_n)
        print(json.dumps(data, ensure_ascii=False, indent=2)[:1500])
        return

    n = min(args.n, len(items))
    tag = f"hard{args.neg}" if args.hard else f"rand{args.neg}"
    out_path = os.path.join(HERE, f"rerank-calib-{tag}-{n}.jsonl")
    out = open(out_path, "w", encoding="utf-8")
    total = 0
    ok = 0
    for i, it in enumerate(items[:n], 1):
        cands = build_pair(items, it, rng, args.neg, args.hard)
        docs = [c["text"] for c in cands]
        try:
            data = rerank(it["question"], docs, top_n)
            results = data["output"]["results"]
            scores = {r["index"]: r["relevance_score"] for r in results}
            for idx, c in enumerate(cands):
                c["rerankScore"] = scores.get(idx)
            record = {
                "id": it["id"],
                "question": it["question"],
                "candidates": cands,
                "usage": data.get("usage"),
                "requestId": data.get("request_id"),
            }
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            ok += 1
            u = data.get("usage") or {}
            total += u.get("total_tokens", 0)
            pos_score = cands[0].get("rerankScore")
            rank = sorted(scores.values(), reverse=True).index(pos_score) + 1 if pos_score is not None else None
            print(f"[{i}/{n}] {it['id']} 正例分数={pos_score} 排名={rank}/{len(cands)} tokens={u.get('total_tokens')}")
            time.sleep(0.3)
        except Exception as exc:  # noqa: BLE001
            print(f"[{i}/{n}] {it['id']} 失败: {exc}")
    out.close()
    print(f"\n完成 {ok}/{n} 题，usage 合计 {total} token，输出 {out_path}")


if __name__ == "__main__":
    main()
