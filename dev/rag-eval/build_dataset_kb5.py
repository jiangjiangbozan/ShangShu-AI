# -*- coding: utf-8 -*-
"""基于知识库切片生成评测集：直接用百炼 qwen-plus HTTP 接口出题。

为什么不复用 build_dataset.py 的 chat_once：
- 后端 /api/v1/model/ping 的 deepseek / qwen 模型 api_key 未配置，直连 401；
- 本地 Ollama 生成一题约 100 秒，80 题不可接受；
- qwen-plus 直调约 1 秒/题，且不占用 embedding / rerank 的额度。

产出 dataset-kb5.jsonl，标注规则与 build_dataset.py 一致（vectorId 作为 relevantChunkIds）。
"""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.stdout.reconfigure(encoding="utf-8")

import eval_common  # noqa: E402
from build_dataset import list_chunks, list_documents, pick_field  # noqa: E402

ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
MODEL = "qwen-plus"
PROMPT = (
    "你是评测集构建助手。根据下面的资料，站在提问者角度生成一个可以用该资料回答的问题。"
    "要求：只输出问题本身，不要编号、引号或其他说明。\n\n资料："
)


def gen_question(text: str, key: str) -> str:
    body = {
        "model": MODEL,
        "messages": [{"role": "user", "content": PROMPT + text[:1200]}],
        "temperature": 0.3,
        "max_tokens": 200,
    }
    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return (payload["choices"][0]["message"]["content"] or "").strip().strip('"').splitlines()[0].strip()


def main() -> None:
    ap = argparse.ArgumentParser(description="生成评测集（qwen-plus 直调）")
    ap.add_argument("--kb-id", required=True, type=int)
    ap.add_argument("--count", type=int, default=80)
    ap.add_argument("--per-doc", type=int, default=2)
    ap.add_argument("--min-chars", type=int, default=80)
    ap.add_argument("--shuffle", action="store_true")
    ap.add_argument("--output", default="dataset-kb5.jsonl")
    args = ap.parse_args()

    key = os.environ.get("DASHSCOPE_API_KEY") or ""
    if not key:
        sys.exit("缺少 DASHSCOPE_API_KEY")

    token, csrf = eval_common.login()
    docs = list_documents(token, csrf, args.kb_id)
    if args.shuffle:
        random.seed(42)
        random.shuffle(docs)
    print(f"文档 {len(docs)} 篇，目标 {args.count} 题")

    items: list[dict] = []
    for doc in docs:
        if len(items) >= args.count:
            break
        doc_id = pick_field(doc, ("id", "docId", "documentId"))
        if doc_id is None:
            continue
        chunks = list_chunks(token, csrf, doc_id)
        taken = 0
        for chunk in chunks:
            if len(items) >= args.count or taken >= args.per_doc:
                break
            chunk_id = pick_field(chunk, ("id", "chunkId"))
            text = str(pick_field(chunk, ("content", "text", "chunkText")) or "")
            if not chunk_id or len(text) < args.min_chars:
                continue
            try:
                question = gen_question(text, key)
            except Exception as exc:  # noqa: BLE001
                print(f"  [warn] 生成失败 doc={doc_id}: {str(exc)[:120]}")
                continue
            if not question:
                continue
            vector_id = pick_field(chunk, ("vectorId", "vector_id"))
            items.append({
                "id": f"q{len(items) + 1:03d}",
                "question": question,
                "relevantChunkIds": [str(vector_id) if vector_id else str(chunk_id)],
                "sourceDocId": doc_id,
                "note": str(pick_field(doc, ("name", "title", "fileName")) or "")[:100],
                "sourceChunkExcerpt": text[:120],
            })
            taken += 1
            print(f"[{len(items)}/{args.count}] {question[:60]}")

    with open(args.output, "w", encoding="utf-8") as fp:
        for item in items:
            fp.write(json.dumps(item, ensure_ascii=False) + "\n")
    print(f"完成：{len(items)} 题写入 {args.output}")


if __name__ == "__main__":
    main()
