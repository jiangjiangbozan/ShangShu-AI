# -*- coding: utf-8 -*-
"""把 enrich/*.jsonl 回填到 knowledge_document 的 summary / keywords / qa_json 字段。

生成 backfill.sql（纯 SQL 文本，转义已处理），由 mysql 客户端执行。
不调用任何模型。
"""

import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "backfill.sql")

lines = []
count = 0
for path in sorted(glob.glob(os.path.join(HERE, "enrich", "enrich-kb*.jsonl"))):
    for line in open(path, encoding="utf-8"):
        if not line.strip():
            continue
        r = json.loads(line)
        summary = (r.get("summary") or "").replace("'", "''").replace("\n", " ")
        keywords = ",".join(r.get("keywords") or []).replace("'", "''")[:250]
        qa = json.dumps(r.get("qa") or [], ensure_ascii=False).replace("'", "''")
        lines.append(
            "UPDATE knowledge_document SET "
            f"summary='{summary}', keywords='{keywords}', qa_json='{qa}' "
            f"WHERE id={int(r['docId'])};"
        )
        count += 1

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"生成 {count} 条 UPDATE -> {OUT}")
