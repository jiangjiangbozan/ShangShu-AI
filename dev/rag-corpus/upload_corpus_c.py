# -*- coding: utf-8 -*-
"""批次 C 语料入库：Kubernetes 中文官方文档（HTML 站点抓取）。

依赖后端已启动、账号可登录；复用 dev/rag-eval 的登录与接口封装。
知识库按名称复用（不存在则新建），文件按序号分批上传。

用法：
  python upload_corpus_c.py            # 全部上传
  python upload_corpus_c.py --dry-run  # 只打印清单
"""

from __future__ import annotations

import glob
import json
import os
import sys
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
EVAL_DIR = os.path.normpath(os.path.join(HERE, "..", "rag-eval"))
sys.path.insert(0, EVAL_DIR)
sys.stdout.reconfigure(encoding="utf-8")

import eval_common  # noqa: E402

CORPUS_DIR = os.path.join(HERE, "batch-c")
KB_NAME = "中文技术文档库"
KB_DESC = "Kubernetes 中文官方文档精选（批次 C 语料，CC BY 4.0）"
BATCH_SIZE = 6


def ensure_kb(token: str, csrf: str) -> int:
    for kb in eval_common.api("GET", "/api/v1/knowledge", token=token, csrf=csrf) or []:
        if kb.get("name") == KB_NAME:
            print(f"复用已有知识库 id={kb.get('id')}（{KB_NAME}）")
            return int(kb.get("id"))
    kb = eval_common.api(
        "POST",
        "/api/v1/knowledge",
        token=token,
        csrf=csrf,
        body={"name": KB_NAME, "description": KB_DESC},
    )
    print(f"新建知识库 id={kb.get('id')}（{KB_NAME}）")
    return int(kb.get("id"))


def batch_upload(kb_id: int, token: str, csrf: str, paths: list[str]):
    boundary = "----ShangShu" + uuid.uuid4().hex
    body = b""
    for path in paths:
        filename = os.path.basename(path)
        with open(path, "rb") as handle:
            content = handle.read()
        body += f"--{boundary}\r\n".encode()
        body += f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'.encode()
        body += b"Content-Type: text/markdown; charset=utf-8\r\n\r\n"
        body += content + b"\r\n"
    body += f"--{boundary}--\r\n".encode()

    url = eval_common.BASE_URL + f"/api/v1/knowledge/{kb_id}/batch-upload"
    request = urllib.request.Request(url, data=body, method="POST")
    request.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    request.add_header("Authorization", "Bearer " + token)
    if csrf:
        request.add_header("X-CSRF-TOKEN", csrf)
    with urllib.request.urlopen(request, timeout=900) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if isinstance(payload, dict) and payload.get("code") not in (0, None):
        raise RuntimeError(payload)
    return payload.get("data")


def main() -> None:
    paths = sorted(glob.glob(os.path.join(CORPUS_DIR, "*.md")))
    if not paths:
        sys.exit(f"语料目录为空：{CORPUS_DIR}")
    total_bytes = sum(os.path.getsize(p) for p in paths)
    print(f"待上传 {len(paths)} 篇，原始字节 {total_bytes}")
    if "--dry-run" in sys.argv:
        for p in paths:
            print(" -", os.path.basename(p))
        return

    token, csrf = eval_common.login()
    kb_id = ensure_kb(token, csrf)

    uploaded = failed = 0
    for i in range(0, len(paths), BATCH_SIZE):
        chunk = paths[i : i + BATCH_SIZE]
        names = [os.path.basename(p) for p in chunk]
        try:
            data = batch_upload(kb_id, token, csrf, chunk)
            print(f"[{i // BATCH_SIZE + 1}] 提交 {len(chunk)} 篇 -> {json.dumps(data, ensure_ascii=False)[:160]}")
            uploaded += len(chunk)
        except Exception as exc:  # noqa: BLE001
            print(f"[{i // BATCH_SIZE + 1}] 失败 {names}: {exc}")
            failed += len(chunk)

    print(f"\n提交完成：成功 {uploaded}，失败 {failed}，知识库 id={kb_id}")
    print("下一步：等异步解析+切片+向量化结束，再统计 document/chunk 数量。")


if __name__ == "__main__":
    main()
