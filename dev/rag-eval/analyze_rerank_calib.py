# -*- coding: utf-8 -*-
"""分析 rerank-calib-80.jsonl：判别力、分数分布、阈值标定（纯本地，不调 API）"""

import json
import os
import statistics
import sys

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
PATH = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "rerank-calib-80.jsonl")

rows = [json.loads(l) for l in open(PATH, encoding="utf-8") if l.strip()]
print(f"样本题数：{len(rows)}")

top1 = 0
pos_scores, neg_scores = [], []
fail_cases = []
for r in rows:
    cands = r["candidates"]
    pos = cands[0]
    ps = pos.get("rerankScore")
    ns = [c.get("rerankScore") for c in cands[1:]]
    pos_scores.append(ps)
    neg_scores.extend([x for x in ns if x is not None])
    best = max([ps] + [x for x in ns if x is not None])
    if ps is not None and abs(ps - best) < 1e-9:
        top1 += 1
    else:
        fail_cases.append((r["id"], round(ps, 3), r.get("requestId")))

print(f"正例 top1 率：{top1}/{len(rows)} = {top1/len(rows):.1%}")
print(f"非 top1 用例：{[(f[0], f[1]) for f in fail_cases]}")

pos_sorted = sorted(pos_scores)
def pct(p):
    idx = min(len(pos_sorted) - 1, int(len(pos_sorted) * p))
    return pos_sorted[idx]

print("\n正例分数分位：P10={:.3f} P25={:.3f} P50={:.3f} P75={:.3f} P90={:.3f}".format(
    pct(0.1), pct(0.25), pct(0.5), pct(0.75), pct(0.9)))
print("负例分数：中位 {:.4f}  P90 {:.4f}  max {:.4f}".format(
    statistics.median(neg_scores), sorted(neg_scores)[int(len(neg_scores)*0.9)], max(neg_scores)))

print("\n阈值扫描（正例保留率 / 负例误放率）：")
print("| 阈值 | 正例保留 | 负例误放 |")
print("|---|---|---|")
for t in (0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8):
    keep = sum(1 for s in pos_scores if s >= t) / len(pos_scores)
    false_pos = sum(1 for s in neg_scores if s >= t) / len(neg_scores)
    print(f"| {t:.1f} | {keep:.1%} | {false_pos:.1%} |")

print("\n正例分数 <0.2 的题（低分但仍需区分）：")
for r in rows:
    ps = r["candidates"][0].get("rerankScore")
    if ps is not None and ps < 0.2:
        best = max([ps] + [c.get("rerankScore") for c in r["candidates"][1:] if c.get("rerankScore") is not None])
        rank = "top1" if abs(ps - best) < 1e-9 else "非top1"
        print(f"  {r['id']} 正例={ps:.3f} ({rank}) q={r['question'][:40]}")
