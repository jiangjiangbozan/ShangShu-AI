# -*- coding: utf-8 -*-
"""批次 A 语料入库：项目自文档 + 技术栈知识文档。

用法（后端已启动、账号可登录）：
  python upload_corpus.py core   # 核心 10 篇：README / 架构 / 工作流 / API / 评测报告
  python upload_corpus.py stack  # docs/kb 技术栈知识文档（含 ADR、agent、stack）

语料直接取自仓库内文件，不另存副本；knowledge_base 名重复时复用已有库。
"""
import json
import os
import sys
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.stdout.reconfigure(encoding="utf-8")
import eval_common

REPO_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

KB_NAME = "技术文档库"
KB_DESC = "尚枢 AI 项目自文档与技术栈知识（批次 A 语料）"

CORE = [
    "README.md",
    "docs/README.md",
    "docs/platform/architecture.md",
    "docs/platform/guides/workflow-node-spi-guide.md",
    "docs/platform/guides/workflow-nodes.md",
    "docs/platform/api/auth.md",
    "docs/platform/api/chat.md",
    "docs/platform/api/system.md",
    "docs/platform/api/model-config.md",
    "docs/platform/testing/rag-eval-report.md",
]


def stack_files():
    root = os.path.join(REPO_ROOT, "docs", "kb")
    result = []
    for dirpath, _, filenames in os.walk(root):
        for name in sorted(filenames):
            if name.endswith(".md"):
                result.append(os.path.relpath(os.path.join(dirpath, name), REPO_ROOT))
    return result


def ensure_kb(token, csrf):
    for kb in eval_common.api("GET", "/api/v1/knowledge", token=token, csrf=csrf) or []:
        if kb.get("name") == KB_NAME:
            print("复用已有知识库 id =", kb.get("id"))
            return kb.get("id")
    kb = eval_common.api(
        "POST", "/api/v1/knowledge", token=token, csrf=csrf,
        body={"name": KB_NAME, "description": KB_DESC})
    print("新建知识库 id =", kb.get("id"))
    return kb.get("id")


def batch_upload(kb_id, token, csrf, rel_paths):
    boundary = "----ShangShu" + uuid.uuid4().hex
    body = b""
    for rel in rel_paths:
        filename = os.path.basename(rel)
        with open(os.path.join(REPO_ROOT, rel), "rb") as handle:
            content = handle.read()
        body += f"--{boundary}\r\n".encode()
        body += (
            f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'
        ).encode()
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


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "core"
    rel_paths = CORE if mode == "core" else stack_files()
    print(f"待上传 {len(rel_paths)} 个文件")
    token, csrf = eval_common.login()
    kb_id = ensure_kb(token, csrf)
    result = batch_upload(kb_id, token, csrf, rel_paths)
    print(json.dumps(result, ensure_ascii=False, indent=2)[:2000])


if __name__ == "__main__":
    main()
