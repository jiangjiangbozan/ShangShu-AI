#!/usr/bin/env python3
"""用百炼 qwen3.7-text-embedding-flash 原地重算 ES 中已有分片的 dense_vector。

为什么要原地重算而不是重新入库：
- embedding 后端从 Ollama nomic-embed-text 切到百炼后向量空间改变，旧向量与新查询不同分布，语义检索会失效；
- 重新上传文档会重新分片、生成新的 chunk id，评测数据集里的 relevantChunkIds 全部作废；
- 直接按 _id 更新 embedding 字段，分片文本、元数据、chunk id 全部保持不变，历史基线仍可对比。

仅用标准库，无需第三方依赖。

用法：
    $env:DASHSCOPE_API_KEY = "sk-..."
    python reembed.py --index ai_agent_knowledge --dry-run     # 只统计规模
    python reembed.py --index ai_agent_knowledge               # 实际重嵌入
    python reembed.py --index ai_agent_knowledge --limit 40    # 抽样试跑
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

ES_URL = os.environ.get("ES_URL", "http://localhost:9200").rstrip("/")
DASHSCOPE_HOST = os.environ.get("DASHSCOPE_HOST", "https://dashscope.aliyuncs.com").rstrip("/")
EMBED_PATH = "/compatible-mode/v1/embeddings"
MODEL = os.environ.get("RAG_EMBEDDING_MODEL", "qwen3.7-text-embedding-flash")
DIMS = int(os.environ.get("RAG_EMBEDDING_DIMENSIONS", "768"))
MAX_BATCH = 20          # 百炼官方单次输入上限
MAX_CHARS = 8000        # 单条输入字符保护上限
INDEX = "ai_agent_knowledge"


def http_call(method: str, url: str, payload=None, headers=None, timeout: int = 90) -> bytes:
    hdrs = dict(headers or {})
    if payload is None:
        body = None
    elif isinstance(payload, (bytes, bytearray)):
        body = bytes(payload)
    else:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        hdrs.setdefault("Content-Type", "application/json")
    request = urllib.request.Request(url, data=body, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "ignore")[:300]
        raise RuntimeError(f"HTTP {exc.code}: {detail}") from exc


def call_json(method: str, url: str, payload=None, headers=None, timeout: int = 90) -> dict:
    raw = http_call(method, url, payload, headers, timeout)
    return json.loads(raw.decode("utf-8")) if raw else {}


def resolve_key() -> str:
    key = os.environ.get("DASHSCOPE_API_KEY") or os.environ.get("RAG_EMBEDDING_API_KEY") or ""
    if not key:
        sys.exit("缺少 DASHSCOPE_API_KEY / RAG_EMBEDDING_API_KEY 环境变量")
    return key


def scroll_all(index: str, page: int, kb_id: str | None = None):
    """遍历索引中的文档，返回 (id, content)；kb_id 非空时只取该知识库的分片。"""
    if kb_id:
        query = {"term": {"metadata.knowledgeBaseId.keyword": str(kb_id)}}
    else:
        query = {"match_all": {}}
    body = {"size": page, "_source": ["content"], "query": query}
    data = call_json("POST", f"{ES_URL}/{index}/_search?scroll=3m", body, timeout=30)
    scroll_id = data["_scroll_id"]
    try:
        while True:
            hits = data["hits"]["hits"]
            for hit in hits:
                yield hit["_id"], (hit["_source"].get("content") or "")
            if not hits:
                return
            data = call_json(
                "POST",
                f"{ES_URL}/_search/scroll",
                {"scroll": "3m", "scroll_id": scroll_id},
                timeout=30,
            )
            scroll_id = data["_scroll_id"]
    finally:
        try:
            http_call(
                "DELETE",
                f"{ES_URL}/_search/scroll",
                {"scroll_id": scroll_id},
                timeout=10,
            )
        except Exception:  # noqa: BLE001
            pass


def embed_batch(key: str, texts: list[str]) -> tuple[list[list[float]], int]:
    payload = {
        "model": MODEL,
        "input": [t[:MAX_CHARS] if t else "" for t in texts],
        "dimensions": DIMS,
        "encoding_format": "float",
    }
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    last_error = None
    for attempt in range(4):
        try:
            parsed = call_json("POST", DASHSCOPE_HOST + EMBED_PATH, payload, headers, timeout=90)
            data = parsed.get("data") or []
            if len(data) != len(texts):
                raise RuntimeError(f"返回条数不符：{len(data)} != {len(texts)}")
            tokens = int((parsed.get("usage") or {}).get("total_tokens") or 0)
            return [item["embedding"] for item in data], tokens
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if attempt == 3:
                break
            wait = 2 ** attempt
            print(f"  [warn] embedding 失败（{exc}），{wait}s 后重试", flush=True)
            time.sleep(wait)
    raise RuntimeError(f"embedding 连续失败：{last_error}")


def bulk_update(index: str, records: list[tuple[str, list[float]]]) -> None:
    lines = []
    for doc_id, vector in records:
        lines.append(json.dumps({"update": {"_index": index, "_id": doc_id}}, ensure_ascii=False))
        lines.append(json.dumps({"doc": {"embedding": vector}}, ensure_ascii=False))
    payload = ("\n".join(lines) + "\n").encode("utf-8")
    result = call_json(
        "POST",
        f"{ES_URL}/_bulk?refresh=false",
        payload,
        {"Content-Type": "application/x-ndjson"},
        timeout=180,
    )
    if result.get("errors"):
        for item in result.get("items", []):
            err = item.get("update", {}).get("error")
            if err:
                raise RuntimeError(f"bulk 写入失败：{json.dumps(err, ensure_ascii=False)}")


def main() -> None:
    global INDEX
    parser = argparse.ArgumentParser(description="原地重嵌入 ES 分片向量")
    parser.add_argument("--index", default=INDEX)
    parser.add_argument("--page", type=int, default=200, help="scroll 每页条数")
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 条，0 表示全部")
    parser.add_argument("--dry-run", action="store_true", help="只统计，不调 API 不写 ES")
    parser.add_argument("--skip-ids", default="", help="跳过 id 列表文件（每行一个）")
    parser.add_argument("--kb-id", default="", help="只处理指定知识库的分片（metadata.knowledgeBaseId）")
    args = parser.parse_args()

    INDEX = args.index
    key = resolve_key() if not args.dry_run else ""
    skip_ids = set()
    if args.skip_ids:
        with open(args.skip_ids, encoding="utf-8") as fp:
            skip_ids = {line.strip() for line in fp if line.strip()}

    started = time.time()
    seen = done = skipped = tokens = chars = 0
    pending_ids: list[str] = []
    pending_texts: list[str] = []

    def flush() -> None:
        nonlocal done, tokens
        if not pending_ids:
            return
        if args.dry_run:
            done += len(pending_ids)
            pending_ids.clear()
            pending_texts.clear()
            return
        vectors, used = embed_batch(key, pending_texts)
        bulk_update(INDEX, list(zip(pending_ids, vectors)))
        done += len(pending_ids)
        tokens += used
        pending_ids.clear()
        pending_texts.clear()

    print(f"[reembed] index={INDEX} model={MODEL} dims={DIMS} dry_run={args.dry_run}", flush=True)
    for doc_id, content in scroll_all(INDEX, args.page, args.kb_id or None):
        if args.limit and seen >= args.limit:
            break
        seen += 1
        if doc_id in skip_ids or not content.strip():
            skipped += 1
            continue
        pending_ids.append(doc_id)
        pending_texts.append(content)
        chars += len(content)
        if len(pending_ids) >= MAX_BATCH:
            flush()
        if seen % 100 == 0:
            print(f"  进度 {seen} 条，已写 {done} 条，累计 {tokens} tokens", flush=True)
    flush()

    elapsed = time.time() - started
    print(
        f"[reembed] 完成：扫描 {seen}，更新 {done}，跳过 {skipped}，"
        f"字符 {chars}，消耗 {tokens} tokens，用时 {elapsed:.1f}s",
        flush=True,
    )


if __name__ == "__main__":
    main()
