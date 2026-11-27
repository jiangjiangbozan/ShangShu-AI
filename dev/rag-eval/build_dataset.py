# -*- coding: utf-8 -*-
"""构建 RAG 评测数据集：从知识库采样 chunk，用 LLM 依据 chunk 内容生成问题并标注来源 chunkId。

产出 dataset.jsonl，每行：
  {"id": "q001", "question": "...", "relevantChunkIds": ["chunk-xx"], "sourceDocId": 1, "note": "..."}
"""

import argparse
import json
import random
import sys

import eval_common


def list_documents(token, csrf, kb_id):
    """拉取知识库文档列表（兼容 PageResult.list / records / 数组三种返回形态）。"""
    data = eval_common.api("GET", f"/api/v1/knowledge/{kb_id}/documents?page=1&size=50",
                           token=token, csrf=csrf)
    if isinstance(data, dict):
        for key in ("list", "records", "rows", "items"):
            if isinstance(data.get(key), list):
                return data[key]
    if isinstance(data, list):
        return data
    return []


def list_chunks(token, csrf, doc_id):
    """拉取文档下的 chunk 列表（同样兼容多种返回形态）。"""
    data = eval_common.api("GET", f"/api/v1/knowledge/documents/{doc_id}/chunks?page=1&size=50",
                           token=token, csrf=csrf)
    if isinstance(data, dict):
        for key in ("list", "records", "rows", "items"):
            if isinstance(data.get(key), list):
                return data[key]
    if isinstance(data, list):
        return data
    return []


def pick_field(item, keys):
    """从字典里按候选键名取第一个非空值。"""
    for key in keys:
        value = item.get(key)
        if value is not None:
            return value
    return None


def generate_question(token, csrf, model_id, text):
    """让 LLM 基于单个 chunk 生成用户会问的问题（只输出问题本身）。"""
    prompt = (
        "你是评测集构建助手。根据下面的资料，站在提问者角度生成一个可以用该资料回答的问题。"
        "要求：只输出问题本身，不要编号、引号或其他说明。\n\n资料：" + text
    )
    question = eval_common.chat_once(token, csrf, model_id, prompt)
    return question.strip().strip('"').strip("“”").splitlines()[0].strip()


def main():
    parser = argparse.ArgumentParser(description="构建 RAG 评测数据集")
    parser.add_argument("--kb-id", required=True, help="知识库 ID")
    parser.add_argument("--count", type=int, default=50, help="题目数量（默认 50）")
    parser.add_argument("--model-id", default=eval_common.MODEL_ID, help="生成问题用的模型")
    parser.add_argument("--min-chars", type=int, default=60, help="chunk 最小长度过滤（默认 60）")
    parser.add_argument("--output", default="dataset.jsonl", help="输出文件")
    parser.add_argument("--shuffle", action="store_true", help="打乱文档顺序，避免题目集中在少数文档")
    parser.add_argument("--per-doc", type=int, default=2, help="每篇文档最多取几个 chunk 出题")
    args = parser.parse_args()

    token, csrf = eval_common.login()
    print(f"登录成功，知识库 {args.kb_id}，目标题数 {args.count}")

    docs = list_documents(token, csrf, args.kb_id)
    if args.shuffle:
        random.seed(42)
        random.shuffle(docs)
    print(f"获取到 {len(docs)} 篇文档")
    if not docs:
        sys.exit("知识库没有文档，请先入库测试文档")

    items = []
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
            text = pick_field(chunk, ("content", "text", "chunkText")) or ""
            if not chunk_id or len(str(text)) < args.min_chars:
                continue
            question = generate_question(token, csrf, args.model_id, str(text)[:1200])
            if not question:
                continue
            # 标注用 vectorId（ES 文档 id）：检索 trace 的 chunkId 即该 id（KnowledgeChunkMapper
            # 按 vector_id 匹配），用 MySQL 主键 id 会导致评测全部判 0
            vector_id = pick_field(chunk, ("vectorId", "vector_id"))
            relevant = str(vector_id) if vector_id else str(chunk_id)
            items.append({
                "id": f"q{len(items) + 1:03d}",
                "question": question,
                "relevantChunkIds": [relevant],
                "sourceDocId": doc_id,
                "note": str(pick_field(doc, ("name", "title", "fileName")) or "")[:100],
                "sourceChunkExcerpt": str(text)[:120],  # 便于人工核对题目与来源 chunk 的对应关系
            })
            print(f"[{len(items)}/{args.count}] {question[:50]}")
            taken += 1

    with open(args.output, "w", encoding="utf-8") as f:
        for item in items:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")
    print(f"完成：{len(items)} 题写入 {args.output}")


if __name__ == "__main__":
    main()
