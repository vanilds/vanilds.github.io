#!/usr/bin/env python3
"""Fails if a dashboard or rule uses a metric that Alloy does not forward to Grafana Cloud.

Alloy keeps only the metric names in its allowlist (alloy/config.alloy, prometheus.relabel "allowlist") to stay
inside the free tier. A panel on a name outside it would simply be empty, with no error anywhere — so CI checks.
"""
import json
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).parent
config = (HERE / "alloy/config.alloy").read_text()
allow = re.search(r'action\s*=\s*"keep"\s*\n\s*regex\s*=\s*"([^"]+)"', config).group(1)
allowed = re.compile(f"^(?:{allow})$")

rules = yaml.safe_load((HERE / "rules/creditdesk.yaml").read_text())
recorded = {r["record"] for g in rules["groups"] for r in g["rules"] if "record" in r}
exprs = [r["expr"] for g in rules["groups"] for r in g["rules"]]
for board in (HERE / "dashboards").glob("*.json"):
    exprs += [t["expr"] for p in json.loads(board.read_text())["panels"] for t in p.get("targets", [])]

NOT_METRICS = {"sum", "rate", "irate", "increase", "histogram_quantile", "topk", "max", "min", "avg", "count",
               "absent", "time", "clamp_min", "by", "without", "on", "ignoring", "group_left", "group_right",
               "and", "or", "unless", "bool", "offset", "le"}
used = set()
for expr in exprs:
    bare = re.sub(r'"[^"]*"', "", expr)                       # strings (label values, regexes)
    bare = re.sub(r"\{[^}]*\}|\[[^\]]*\]", "", bare)           # selectors and ranges
    bare = re.sub(r"\b(by|without|on|ignoring|group_left|group_right)\s*\([^)]*\)", "", bare)  # label lists
    for name in re.findall(r"[a-zA-Z_:][a-zA-Z0-9_:]*", bare):
        if name not in NOT_METRICS and name not in recorded and not re.fullmatch(r"\d.*|e\d*", name):
            used.add(name)

missing = sorted(n for n in used if not allowed.match(n))
if missing:
    print("Used by a dashboard or rule but not forwarded by Alloy (add to the allowlist in alloy/config.alloy):")
    print("\n".join(f"  {n}" for n in missing))
    sys.exit(1)
print(f"allowlist OK: {len(used)} metric names used, all forwarded")
