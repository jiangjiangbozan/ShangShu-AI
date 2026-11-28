# -*- coding: utf-8 -*-
"""把 knowledge_document.summary（中文）批量翻译为英文并回填 summary_en。

模型：qwen-mt-uni（已登记 docs/platform/testing/model-usage-policy.md §二/§七）。
协议：百炼 OpenAI 兼容端点，请求体携带 input.source_texts / target_lang，
响应为 Data.TranslatedTexts 字符串数组（非标准 chat completions 结构）。
流程：mysql 客户端导出 (id, summary) -> 分批调用（每批 20 条）-> 生成
backfill_summary_en.sql（转义已处理）-> 由 mysql 客户端执行。
密钥从环境变量 DASHSCOPE_API_KEY 或 application-secret.yml 读取，不落仓库。

用法：
    python backfill_summary_en.py --dry-run   # 只生成 SQL 不执行
    python backfill_summary_en.py             # 生成 SQL 并执行回填
"""

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "backfill_summary_en.sql")

MYSQL = r"D:\application\FlyEnv-Data\env\mysql\bin\mysql.exe"
DB_ARGS = [
    "--user=yunzhi",
    "--password=yunzhi",
    "--host=127.0.0.1",
    "--port=3306",
    "--database=ai_agent",
    "--default-character-set=utf8mb4",
    "--batch",
    "--raw",
]

BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
MODEL = "qwen-mt-uni"
BATCH_SIZE = 20
TARGET_LANG = "English"
MAX_EN_CHARS = 500  # summary_en 列宽 512，留余量


def load_api_key():
    key = os.environ.get("DASHSCOPE_API_KEY", "").strip()
    if key:
        return key
    secret = r"D:\ai\api\src\main\resources\application-secret.yml"
    if os.path.exists(secret):
        for line in open(secret, encoding="utf-8"):
            m = re.search(r"api-key:\s*(\S+)", line)
            if m:
                return m.group(1)
    raise SystemExit("未找到 DASHSCOPE_API_KEY（环境变量或 application-secret.yml）")


def fetch_summaries():
    """导出 (docId, summary) 列表，--batch 模式需反转义 \\n \\t。"""
    sql = (
        "SELECT id, summary FROM knowledge_document "
        "WHERE deleted=0 AND summary IS NOT NULL AND summary <> '' "
        "AND (summary_en IS NULL OR summary_en = '') ORDER BY id;"
    )
    out = subprocess.run(
        [MYSQL] + DB_ARGS + ["-e", sql], capture_output=True, check=True
    ).stdout.decode("utf-8")

    rows = []
    for line in out.splitlines():
        if not line.strip() or line.startswith("id\t"):
            continue
        doc_id, _, summary = line.partition("\t")
        summary = (
            summary.replace("\\n", " ")
            .replace("\\t", " ")
            .replace("\\\\", "\\")
            .strip()
        )
        if summary:
            rows.append((int(doc_id), summary))
    return rows


def translate_batch(api_key, texts):
    body = json.dumps(
        {
            "model": MODEL,
            "input": {
                "source_texts": texts,
                "source_lang": "auto",
                "target_lang": TARGET_LANG,
            },
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        BASE_URL + "/chat/completions",
        data=body,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    if not data.get("Success", True):
        raise RuntimeError(f"翻译失败: {data.get('Message')}")
    translated = data["Data"]["TranslatedTexts"]
    if len(translated) != len(texts):
        raise RuntimeError(f"返回条数不匹配: 期望 {len(texts)} 实际 {len(translated)}")
    return translated


def sql_escape(text):
    return text.replace("\\", "\\\\").replace("'", "''")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只生成 SQL 不执行")
    args = parser.parse_args()

    api_key = load_api_key()
    rows = fetch_summaries()
    total_chars = sum(len(s) for _, s in rows)
    print(f"待翻译 {len(rows)} 篇摘要，共 {total_chars} 字，约 {len(rows) // BATCH_SIZE + 1} 次请求")

    statements = []
    tokens_in = tokens_out = 0
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i : i + BATCH_SIZE]
        ids = [doc_id for doc_id, _ in batch]
        texts = [s for _, s in batch]
        translated = translate_batch(api_key, texts)
        for doc_id, summary, en in zip(ids, texts, translated):
            en = en.strip()[:MAX_EN_CHARS]
            statements.append(
                f"UPDATE knowledge_document SET summary_en='{sql_escape(en)}' "
                f"WHERE id={doc_id};"
            )
        tokens_in += sum(len(s) for s in texts)
        tokens_out += sum(len(s) for s in translated)
        done = min(i + BATCH_SIZE, len(rows))
        print(f"  [{done}/{len(rows)}] 已翻译（累计输入≈{tokens_in}字 输出≈{tokens_out}字）")

    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(statements))
    print(f"生成 {len(statements)} 条 UPDATE -> {OUT}")

    if args.dry_run:
        print("dry-run 结束，未执行回填")
        return

    # 整份 SQL 作为 -e 参数会超 Windows 命令行长度上限（WinError 206），改走 stdin
    with open(OUT, "rb") as sql_file:
        subprocess.run([MYSQL] + DB_ARGS, stdin=sql_file, check=True)
    print(f"回填完成：{len(statements)} 条")


if __name__ == "__main__":
    sys.exit(main())
