#!/usr/bin/env python3
"""Checks a "Download Nouride's files" zip against spec/fixtures/backup.json's `out` list.
Usage: backup-check.py <fixture> <zip> <folder-listing>
The listing holds one path per line relative to `.nouride/` (find -type f / -type l), so the excluded paths
that were in the folder are named. Prints a summary; exits 1 when an entry matches an `out` pattern or the
snapshot is missing."""
import json, re, sys, zipfile

def rx(p):
    out, i = "", 0
    while i < len(p):
        if p.startswith("**/", i): out += "(?:[^/]+/)*"; i += 3
        elif p[i] == "*": out += "[^/]*"; i += 1
        else: out += re.escape(p[i]); i += 1
    return re.compile("^" + out + (".*" if p.endswith("/") else "$"))

pats = [(o["pattern"], rx(o["pattern"])) for o in json.load(open(sys.argv[1]))["out"]]
def hit(path): return next((p for p, r in pats if r.match(path)), None)

names = [n for n in zipfile.ZipFile(sys.argv[2]).namelist() if not n.endswith("/")]
bad = [n for n in names if not n.startswith(".nouride/")]
rel = [n[len(".nouride/"):] for n in names if n.startswith(".nouride/")]
inside = [(n, hit(n)) for n in rel if hit(n) and n != "data/nouride.db"]
folder = [l.strip().lstrip("./") for l in open(sys.argv[3]) if l.strip()]
excluded = {}
for f in folder:
    p = hit(f)
    if p: excluded.setdefault(p, []).append(f)
kept_missing = [f for f in folder if not hit(f) and f not in rel]
print(f"entries: {len(names)}; outside .nouride/: {len(bad)}")
print(f"snapshot .nouride/data/nouride.db: {'yes' if 'data/nouride.db' in rel else 'NO'}")
print(f"entries matching an out pattern: {len(inside)}", *[f"  {n} ({p})" for n, p in inside], sep="\n")
print(f"files in the folder: {len(folder)}; matched by out and left out:")
for p, fs in sorted(excluded.items()): print(f"  {p}: {len(fs)} file(s), e.g. {fs[0]}")
print(f"files in the folder not excluded but missing from the zip: {len(kept_missing)}", *[f"  {f}" for f in kept_missing[:20]], sep="\n")
sys.exit(1 if inside or bad or "data/nouride.db" not in rel else 0)
