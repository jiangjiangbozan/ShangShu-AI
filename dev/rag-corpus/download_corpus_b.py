#!/usr/bin/env python3
"""下载「批次 B」精选官方技术文档，落盘为纯文本语料。

选源原则：
1. 只取官方仓库的文档源（不是二手博客），保证权威性；
2. 与项目技术栈直接对应：Spring AI（RAG/Advisor/Tool/可观测）、MyBatis-Plus（ORM/分页/多租户）、
   Elasticsearch（向量检索）、RabbitMQ（异步消息）；
3. 记录仓库、路径、分支、许可证与抓取时间，形成可追溯的 MANIFEST。

产物：
- batch-b/*.md           供知识库入库的纯文本
- MANIFEST.md            来源清单与统计

用法： python download_corpus_b.py
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import urllib.error

RAW_TEMPLATE = "https://raw.githubusercontent.com/{repo}/{branch}/{path}"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "batch-b")

# (仓库, 分支, 源路径, 输出名, 归属技术栈, 许可证)
SOURCES = [
    # ---------- Spring AI Reference（Apache-2.0） ----------
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/rag/index.adoc",
     "spring-ai-rag-overview", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/rag/modules.adoc",
     "spring-ai-rag-modules", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/chatclient/basics.adoc",
     "spring-ai-chatclient-basics", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/chatclient/advisors.adoc",
     "spring-ai-chatclient-advisors", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/advisors/index.adoc",
     "spring-ai-advisors", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/tools/index.adoc",
     "spring-ai-tools", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/tools/reference.adoc",
     "spring-ai-tool-reference", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/structured-output/index.adoc",
     "spring-ai-structured-output", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/observability/index.adoc",
     "spring-ai-observability", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/evaluation/llm-as-judge.adoc",
     "spring-ai-llm-as-judge", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/memory/chat-memory.adoc",
     "spring-ai-chat-memory", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/prompts/index.adoc",
     "spring-ai-prompts", "Spring AI", "Apache-2.0"),
    # ---------- MyBatis-Plus 中文文档（Apache-2.0） ----------
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/getting-started/index.mdx",
     "mybatis-plus-quick-start", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/getting-started/install.mdx",
     "mybatis-plus-install", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/logic-delete.md",
     "mybatis-plus-logic-delete", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/auto-fill-field.md",
     "mybatis-plus-auto-fill", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/id-generator.md",
     "mybatis-plus-id-generator", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/batch-operation.md",
     "mybatis-plus-batch", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/advanced-features.mdx",
     "mybatis-plus-advanced-features", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/sql-injector.mdx",
     "mybatis-plus-sql-injector", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/guides/type-handler.md",
     "mybatis-plus-type-handler", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/plugins/pagination.mdx",
     "mybatis-plus-pagination", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/plugins/tenant.md",
     "mybatis-plus-tenant", "MyBatis-Plus", "Apache-2.0"),
    ("baomidou/mybatis-plus-doc", "master",
     "src/content/docs/plugins/optimistic-locker.md",
     "mybatis-plus-optimistic-lock", "MyBatis-Plus", "Apache-2.0"),
    # ---------- Spring AI 续：语义缓存、多模型、评测、工具调用 Advisor ----------
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/rag/semantic-cache.adoc",
     "spring-ai-semantic-cache", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/tools/tool-calling-advisor.adoc",
     "spring-ai-tool-calling-advisor", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/chatclient/multiple-models.adoc",
     "spring-ai-multiple-models", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/chatclient/responses.adoc",
     "spring-ai-chatclient-responses", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/evaluation/evaluators.adoc",
     "spring-ai-evaluators", "Spring AI", "Apache-2.0"),
    ("spring-projects/spring-ai", "main",
     "spring-ai-docs/src/main/antora/modules/ROOT/pages/chatclient/defaults.adoc",
     "spring-ai-chatclient-defaults", "Spring AI", "Apache-2.0"),
    # ---------- RabbitMQ 官方文档（MPL-2.0 / Apache-2.0） ----------
    ("rabbitmq/rabbitmq-website", "main",
     "docs/amqp.md",
     "rabbitmq-amqp-concepts", "RabbitMQ", "MPL-2.0"),
    ("rabbitmq/rabbitmq-website", "main",
     "docs/queues.md",
     "rabbitmq-queues", "RabbitMQ", "MPL-2.0"),
    ("rabbitmq/rabbitmq-website", "main",
     "docs/consumer-prefetch.md",
     "rabbitmq-consumer-prefetch", "RabbitMQ", "MPL-2.0"),
]


def fetch(url: str) -> str:
    """用系统 curl 下载：urllib 在 raw.githubusercontent.com 上偶发假死，curl 稳定。"""
    result = subprocess.run(
        ["curl", "-sSL", "-m", "40", url],
        capture_output=True,
        timeout=60,
    )
    if result.returncode != 0:
        stderr = result.stderr.decode("utf-8", "replace").strip()
        raise RuntimeError(f"curl exit={result.returncode} {stderr[:200]}")
    return result.stdout.decode("utf-8", "replace")


def cleanup(text: str) -> str:
    """去掉构建期语法噪声（frontmatter、MDX import/export、adoc 属性行）。"""
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            text = text[end + 4 :]
    lines = []
    in_component = False
    for line in text.splitlines():
        stripped = line.strip()
        if re.match(r"^import\s+.+from\s+['\"]", stripped):
            continue
        if re.match(r"^export\s+(const|default|function)", stripped):
            continue
        # Astro/MDX 常用自闭合组件标签，直接丢
        if re.match(r"^<[A-Z][A-Za-z0-9]*\s*[^>]*/>$", stripped):
            continue
        if re.match(r"^:([a-z-]+):\s*", stripped) and not line.startswith(" "):
            continue
        lines.append(line)
        in_component = in_component and not stripped.startswith("</")
    body = "\n".join(lines)
    body = re.sub(r"\n{3,}", "\n\n", body)
    return body.strip() + "\n"


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    records = []
    total_chars = 0
    failed = []

    for i, (repo, branch, path, name, stack, license_name) in enumerate(SOURCES, start=1):
        url = RAW_TEMPLATE.format(repo=repo, branch=branch, path=path)
        try:
            raw = fetch(url)
        except urllib.error.HTTPError as exc:
            failed.append((name, f"HTTP {exc.code}", url))
            print(f"[{i:02d}] FAIL {name}: HTTP {exc.code}", flush=True)
            continue
        except Exception as exc:  # noqa: BLE001
            failed.append((name, str(exc), url))
            print(f"[{i:02d}] FAIL {name}: {exc}", flush=True)
            continue

        body = cleanup(raw)
        target = os.path.join(OUT_DIR, f"{i:02d}-{name}.md")
        with open(target, "w", encoding="utf-8", newline="\n") as fp:
            fp.write(body)
        chars = len(body)
        total_chars += chars
        records.append(
            {
                "order": i,
                "name": name,
                "file": os.path.basename(target),
                "repo": repo,
                "branch": branch,
                "path": path,
                "stack": stack,
                "license": license_name,
                "chars": chars,
                "url": url,
            }
        )
        print(f"[{i:02d}] OK {name} ({chars} chars)", flush=True)

    manifest = []
    manifest.append("# 批次 B 精选官方技术文档清单\n")
    manifest.append("> 全部来自官方仓库文档源（非二手博客），按技术栈归类，供知识库入库与切片统计。\n")
    manifest.append(f"\n- 文档数：{len(records)}")
    manifest.append(f"- 失败数：{len(failed)}")
    manifest.append(f"- 总字符数：{total_chars}")
    manifest.append(f"- 平均字符数：{total_chars // max(len(records), 1)}\n")
    manifest.append("\n| # | 技术栈 | 文档 | 字符数 | 来源 | 许可证 |")
    manifest.append("|---|---|---|---|---|---|")
    for r in records:
        manifest.append(
            f"| {r['order']} | {r['stack']} | {r['name']} | {r['chars']} | "
            f"[{r['repo']}/{os.path.basename(r['path'])}]({r['url']}) | {r['license']} |"
        )
    if failed:
        manifest.append("\n## 抓取失败\n")
        for name, reason, url in failed:
            manifest.append(f"- {name}: {reason} — {url}")

    with open(os.path.join(os.path.dirname(OUT_DIR), "MANIFEST.md"), "w", encoding="utf-8", newline="\n") as fp:
        fp.write("\n".join(manifest) + "\n")
    with open(os.path.join(os.path.dirname(OUT_DIR), "manifest.json"), "w", encoding="utf-8", newline="\n") as fp:
        json.dump({"records": records, "failed": failed, "total_chars": total_chars}, fp, ensure_ascii=False, indent=2)

    print(f"\n[done] {len(records)} 篇，{total_chars} 字符，失败 {len(failed)}")


if __name__ == "__main__":
    main()
