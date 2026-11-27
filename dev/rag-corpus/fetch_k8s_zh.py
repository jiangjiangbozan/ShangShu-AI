#!/usr/bin/env python3
"""抓取「批次 C」中文官方文档（Kubernetes 中文站点）为纯文本语料。

选源说明：
1. 当前网络环境下 GitHub raw / jsDelivr 不可达，站点直连可达；
2. kubernetes.io/zh-cn 是国内可直连的官方中文文档，内容为中文技术说明，与现有英文语料互补；
3. 语料只用于本地知识库检索评测，记录来源 URL、许可（CC BY 4.0）与抓取时间。

产物：
- batch-c/*.md           供知识库入库的纯文本
- MANIFEST_C.md          来源清单与统计
- manifest_c.json        机器可读清单

用法：
  python fetch_k8s_zh.py --limit 85
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from html.parser import HTMLParser

CURL = r"C:\Windows\System32\curl.exe"
BASE = "https://kubernetes.io"
SITEMAP_URL = BASE + "/zh-cn/sitemap.xml"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "batch-c")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")

SECTION_ORDER = ["/docs/concepts/", "/docs/tasks/", "/docs/tutorials/"]
MIN_CHARS = 1500          # 低于此字符数的页面视为索引页/残页，丢弃
MAX_CHARS = 12000         # 单篇上限，避免超长参考页拖垮切片质量


class DocExtractor(HTMLParser):
    """提取 <main> 区域正文，跳过 script/style/nav/header/footer/aside。"""

    SKIP = {"script", "style", "nav", "footer", "header", "aside", "noscript"}
    BLOCK = {"p", "div", "section", "article", "li", "h1", "h2", "h3", "h4",
             "h5", "pre", "td", "th", "blockquote"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.skip_depth = 0
        self.blocks: list[str] = []
        self.cur: list[str] = []
        self.h1: list[str] = []
        self.in_h1 = 0
        self.title: list[str] = []
        self.in_title = 0
        self.pre_depth = 0
        self.cur_is_pre = False

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self.skip_depth += 1
        if tag == "h1":
            self.in_h1 += 1
        if tag == "title":
            self.in_title += 1
        if tag == "pre":
            self.pre_depth += 1

    def handle_endtag(self, tag):
        if tag in self.SKIP and self.skip_depth > 0:
            self.skip_depth -= 1
        if tag == "h1" and self.in_h1 > 0:
            self.in_h1 -= 1
        if tag == "title" and self.in_title > 0:
            self.in_title -= 1
        if tag == "pre" and self.pre_depth > 0:
            self.pre_depth -= 1
        if tag in self.BLOCK and self.cur:
            self._flush()

    def handle_data(self, data):
        if self.in_title:
            if self.skip_depth == 0:
                t = data.strip()
                if t:
                    self.title.append(t)
            return
        if self.skip_depth > 0:
            return
        if self.in_h1:
            t = data.strip()
            if t:
                self.h1.append(t)
        if not data.strip():
            return
        self.cur.append(data if self.pre_depth else data.strip())

    def _flush(self):
        text = "".join(self.cur).strip()
        self.cur = []
        if len(text) > 1:
            self.blocks.append(text)

    def result(self):
        if self.cur:
            self._flush()
        body = "\n\n".join(self.blocks)
        body = re.sub(r"\n{3,}", "\n\n", body).strip() + "\n"
        return body, (self.h1[0].strip() if self.h1 else ""), (self.title[0].strip() if self.title else "")


def curl(args: list[str], timeout: int = 30) -> subprocess.CompletedProcess:
    return subprocess.run([CURL] + args, capture_output=True, text=True, timeout=timeout + 10)


def fetch_text(url: str, retries: int = 2) -> str:
    tmp = os.path.join(tempfile.gettempdir(), "fetch_" + str(abs(hash(url))) + ".html")
    for attempt in range(retries + 1):
        r = curl(["-sL", "-m", "30", "-A", UA, "-o", tmp, "-w", "%{http_code}", url])
        code = (r.stdout or "").strip()
        if code == "200" and os.path.exists(tmp) and os.path.getsize(tmp) > 2048:
            with open(tmp, encoding="utf-8", errors="ignore") as fp:
                return fp.read()
        if attempt < retries:
            time.sleep(1.5)
    raise RuntimeError(f"HTTP {code}")


def load_sitemap_urls() -> list[str]:
    tmp = os.path.join(tempfile.gettempdir(), "k8s_zh_sitemap.xml")
    r = curl(["-sL", "-m", "30", "-A", UA, "-o", tmp, "-w", "%{http_code}", SITEMAP_URL])
    if (r.stdout or "").strip() != "200":
        raise RuntimeError("sitemap 下载失败: " + (r.stdout or ""))
    with open(tmp, encoding="utf-8", errors="ignore") as fp:
        xml = fp.read()
    return [m.group(1) for m in re.finditer(r"<loc>(.*?)</loc>", xml)]


def existing_slugs() -> set[str]:
    """扫描已抓取的语料文件，返回 slug 集合，用于增量抓取去重。"""
    result: set[str] = set()
    if not os.path.isdir(OUT_DIR):
        return result
    for name in os.listdir(OUT_DIR):
        m = re.match(r"\d{2,}-(.+)\.md$", name)
        if m:
            result.add(m.group(1))
    return result


def select_urls(all_urls: list[str], limit: int, sections: list[str], skip: set[str]) -> list[str]:
    """按 分区优先级 + 路径深度 排序，跳过已抓页面，取前 limit 篇。"""
    docs = [u for u in all_urls if "/zh-cn/docs/" in u]
    ranked = []
    for u in docs:
        for idx, sec in enumerate(sections):
            if sec in u:
                rest = u.split(sec, 1)[1].strip("/")
                depth = rest.count("/") + 1 if rest else 0
                # 深度 1 多为分区目录页，正文少，排到同类文章之后
                is_index = 1 if depth <= 1 else 0
                ranked.append((idx, is_index, depth, len(u), u))
                break
    ranked.sort()
    out = []
    for _, _, _, _, u in ranked:
        if u not in out and slug(u) not in skip:
            out.append(u)
    return out[:limit]


def slug(url: str) -> str:
    path = url.split("/zh-cn/", 1)[1].rstrip("/")
    parts = [p for p in path.split("/") if p]
    if parts and parts[0] == "docs":
        parts = parts[1:]
    return "-".join(parts) if parts else "index"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=85)
    ap.add_argument("--sections", default="concepts,tasks,tutorials",
                    help="分区白名单，逗号分隔（concepts/tasks/tutorials/reference/setup）")
    ap.add_argument("--error-log", default="", help="额外输出 JSON 错误清单路径（增量抓取时用）")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    sections = [f"/docs/{s.strip()}/" for s in args.sections.split(",") if s.strip()]
    urls = select_urls(load_sitemap_urls(), args.limit, sections, existing_slugs())
    print(f"[plan] 计划抓取 {len(urls)} 篇（分区 {sections}）", flush=True)
    if args.dry_run:
        for u in urls[:15]:
            print("   ", u)
        return

    os.makedirs(OUT_DIR, exist_ok=True)
    start = 1
    for name in os.listdir(OUT_DIR):
        m = re.match(r"(\d{2,})-", name)
        if m:
            start = max(start, int(m.group(1)) + 1)
    records, failed = [], []
    total_chars = total_cn = 0

    for i, url in enumerate(urls, start=start):
        try:
            html = fetch_text(url)
            p = DocExtractor()
            p.feed(html)
            body, h1, title = p.result()
        except Exception as exc:  # noqa: BLE001
            failed.append({"url": url, "reason": str(exc)})
            print(f"[{i:02d}] FAIL {url} {exc}", flush=True)
            time.sleep(0.4)
            continue

        chars = len(body)
        if chars < MIN_CHARS:
            failed.append({"url": url, "reason": f"太短({chars}字)"})
            print(f"[{i:02d}] SKIP 短页 {chars} {url}", flush=True)
            time.sleep(0.4)
            continue
        if chars > MAX_CHARS:
            body = body[:MAX_CHARS].rstrip() + "\n"

        cn = len(re.findall(r"[\u4e00-\u9fff]", body))
        name = slug(url)
        fname = f"{i:02d}-{name}.md"
        target = os.path.join(OUT_DIR, fname)
        header = f"# {h1 or title or name}\n\n> 来源：{url}\n> 许可证：CC BY 4.0（Kubernetes 文档）\n\n"
        with open(target, "w", encoding="utf-8", newline="\n") as fp:
            fp.write(header + body)

        total_chars += chars
        total_cn += cn
        records.append({"order": i, "file": fname, "url": url, "title": h1 or title,
                        "chars": chars, "cn_chars": cn})
        print(f"[{i:02d}] OK {chars:>6}字 中文{cn:>5}  {name}", flush=True)
        time.sleep(0.25)

    # 合并历史批次记录，避免增量抓取覆盖既有 MANIFEST
    manifest_path = os.path.join(os.path.dirname(OUT_DIR), "manifest_c.json")
    all_records = list(records)
    prev_chars = prev_cn = 0
    try:
        with open(manifest_path, encoding="utf-8") as fp:
            prev = json.load(fp)
        current_files = {r["file"] for r in records}
        prev_records = [r for r in (prev.get("records") or []) if r.get("file") not in current_files]
        prev_chars = sum(int(r.get("chars") or 0) for r in prev_records)
        prev_cn = sum(int(r.get("cn_chars") or 0) for r in prev_records)
        all_records = prev_records + records
    except Exception:  # noqa: BLE001
        pass

    manifest = ["# 批次 C：Kubernetes 中文官方文档语料\n",
                "> 来源 kubernetes.io/zh-cn（CC BY 4.0），HTML 正文提取，用于中文检索能力补强。\n"]
    manifest.append(f"- 文档数：{len(all_records)}")
    manifest.append(f"- 本轮新增：{len(records)}，失败：{len(failed)}")
    manifest.append(f"- 总字符数：{prev_chars + total_chars}")
    manifest.append(f"- 中文字符数：{prev_cn + total_cn}")
    manifest.append(f"- 平均字符数：{(prev_chars + total_chars) // max(len(all_records), 1)}\n")
    manifest.append("| # | 文档 | 字符数 | 中文字 | 来源 |")
    manifest.append("|---|---|---|---|---|")
    for r in all_records:
        manifest.append(f"| {r['order']} | {r['title'][:40]} | {r['chars']} | {r['cn_chars']} | [link]({r['url']}) |")

    with open(os.path.join(os.path.dirname(OUT_DIR), "MANIFEST_C.md"), "w", encoding="utf-8", newline="\n") as fp:
        fp.write("\n".join(manifest) + "\n")
    with open(manifest_path, "w", encoding="utf-8", newline="\n") as fp:
        json.dump({"records": all_records, "failed": failed,
                   "total_chars": prev_chars + total_chars, "total_cn": prev_cn + total_cn},
                  fp, ensure_ascii=False, indent=2)

    print(f"\n[done] {len(records)} 篇 / {total_chars} 字符 / 中文 {total_cn} / 失败 {len(failed)}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
