# -*- coding: utf-8 -*-
"""P0 语料增强：给每篇文档生成「摘要 + 关键词 + 3 组问答对」

只调用 qwen3.8-max-0902（百炼 OpenAI 兼容端点），不触发 embedding / rerank。
数据经后端只读接口取（GET /documents/{id}/chunks），不消耗任何模型额度。

产出 dev/rag-corpus/enrich/enrich-<kb>.jsonl，每行一篇：
{"docId","kbId","title","summary","keywords":[...],"qa":[{"q","a"}...],"usage":{...}}

用法：
  python enrich_corpus.py --kb 3 --limit 3        # 试跑 3 篇
  python enrich_corpus.py --kb 3 --concurrency 4 # 全量并跑
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.normpath(os.path.join(HERE, "..", "rag-eval"))
sys.path.insert(0, EVAL_DIR)
sys.stdout.reconfigure(encoding="utf-8")

import eval_common  # noqa: E402

CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
MODEL = "qwen3.8-max-0902"
MAX_INPUT_CHARS = 3000
OUT_DIR = os.path.join(HERE, "enrich")

SYSTEM_PROMPT = (
    "你是资深技术文档编辑。请根据给定的技术文档片段，输出严格的 JSON 对象，不要输出任何解释、"
    "不要使用 markdown 代码块。字段要求："
    'summary 为 120 字以内中文摘要；keywords 为 5 个中文关键词；qa 为 3 组问答对，'
    "问题必须能在片段中找到依据，答案简明扼要。JSON 格式："
    '{"summary":"...","keywords":["...","...","...","...","..."],'
    '"qa":[{"q":"...","a":"..."},{"q":"...","a":"..."},{"q":"...","a":"..."}]}'
)


def load_key() -> str:
    p = os.path.join(HERE, "..", "..", "api", "src", "main", "resources", "application-secret.yml")
    for line in open(p, encoding="utf-8"):
        s = line.strip()
        if s.startswith("api-key:"):
            return s.split(":", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("未找到 dashscope api-key")


API_KEY = load_key()


def fetch_documents(token: str, csrf: str, kb_id: int) -> list[dict]:
    """按页拉全文档列表（接口默认每页 20 条，这里用 size=100 循环取满）。"""
    all_docs, page = [], 1
    while True:
        batch = eval_common.api(
            "GET", f"/api/v1/knowledge/{kb_id}/documents?page={page}&size=100", token=token, csrf=csrf) or []
        if not batch:
            break
        all_docs.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return [d for d in all_docs if str(d.get("status") or "").lower() in ("completed", "")]


def fetch_text(token: str, csrf: str, doc_id: int, max_chars: int) -> str:
    chunks = eval_common.api(
        "GET", f"/api/v1/knowledge/documents/{doc_id}/chunks", token=token, csrf=csrf) or []
    text, total = [], 0
    for c in chunks:
        content = (c.get("content") or "").strip()
        if not content:
            continue
        text.append(content)
        total += len(content)
        if total >= max_chars:
            break
    return "\n".join(text)[:max_chars]


def parse_json(text: str) -> dict:
    t = text.strip()
    if t.startswith("```"):
        t = t.split("```")[1]
        if t.startswith("json"):
            t = t[4:]
    start, end = t.find("{"), t.rfind("}")
    if start >= 0 and end > start:
        t = t[start : end + 1]
    return json.loads(t)


def enrich_one(title: str, text: str, retries: int = 2) -> tuple[dict, dict]:
    body = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": f"文档标题：{title}\n\n文档片段：\n{text}"},
        ],
        "temperature": 0.3,
        "max_tokens": 1200,
    }
    last_exc = None
    for attempt in range(retries + 1):
        try:
            resp = requests.post(
                CHAT_URL,
                headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
                json=body,
                timeout=180,
            )
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]
            return parse_json(content), data.get("usage") or {}
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            time.sleep(1 + attempt)
    raise last_exc


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--kb", type=int, required=True, help="知识库 id")
    ap.add_argument("--limit", type=int, default=0, help="最多处理 N 篇（0 = 全部）")
    ap.add_argument("--concurrency", type=int, default=3)
    ap.add_argument("--max-input", type=int, default=MAX_INPUT_CHARS)
    ap.add_argument("--budget", type=int, default=800000, help="累计 usage token 上限，达到即停")
    args = ap.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, f"enrich-kb{args.kb}.jsonl")
    done = set()
    if os.path.exists(out_path):
        for line in open(out_path, encoding="utf-8"):
            try:
                done.add(json.loads(line)["docId"])
            except Exception:  # noqa: BLE001
                pass

    token, csrf = eval_common.login()
    docs = fetch_documents(token, csrf, args.kb)
    docs = [d for d in docs if int(d["id"]) not in done]
    if args.limit:
        docs = docs[: args.limit]
    print(f"kb{args.kb}：待处理 {len(docs)} 篇（已存在 {len(done)} 篇），并发 {args.concurrency}")

    out = open(out_path, "a", encoding="utf-8")
    total_tokens = 0
    ok = fail = 0

    def work(doc: dict) -> dict | None:
        text = fetch_text(token, csrf, int(doc["id"]), args.max_input)
        if len(text) < 200:
            print(f"  跳过 {doc['id']}（片段过短 {len(text)} 字）")
            return None
        title = doc.get("fileName") or doc.get("name") or f"doc-{doc['id']}"
        result, usage = enrich_one(title, text)
        record = {
            "docId": int(doc["id"]),
            "kbId": args.kb,
            "title": title,
            "summary": result.get("summary"),
            "keywords": result.get("keywords"),
            "qa": result.get("qa"),
            "usage": usage,
            "chars": len(text),
        }
        return record

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = {pool.submit(work, d): d for d in docs}
        for fut in as_completed(futures):
            doc = futures[fut]
            try:
                rec = fut.result()
                if not rec:
                    continue
                total_tokens += (rec.get("usage") or {}).get("total_tokens", 0)
                out.write(json.dumps(rec, ensure_ascii=False) + "\n")
                out.flush()
                ok += 1
                print(f"  [{ok + fail}/{len(docs)}] {rec['docId']} {rec['title'][:40]} | "
                      f"{str(rec['summary'])[:40]} | tokens={rec['usage'].get('total_tokens')} | 累计={total_tokens}")
            except Exception as exc:  # noqa: BLE001
                fail += 1
                print(f"  失败 {doc.get('id')}: {exc}")
            if total_tokens >= args.budget:
                print(f"  已达预算 {args.budget}，停止提交新任务")
                break
    out.close()
    print(f"\n完成：成功 {ok}，失败 {fail}，usage 合计 {total_tokens}，输出 {out_path}")


if __name__ == "__main__":
    main()
