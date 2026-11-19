#!/bin/sh
# On the Mac: Home Dev's Nouride audit entries after a millisecond timestamp, oldest first, the account masked.
# Usage: mac-audit.sh <ms>
sh "$HOME/nix-work/nr-ctl.sh" GET '/audit?limit=40' | TZ=Asia/Jakarta python3 -c '
import json,sys,datetime,re
since=int(sys.argv[1])
for e in reversed(json.load(sys.stdin)["entries"]):
    if e["timestamp"]>since:
        line=" ".join([datetime.datetime.fromtimestamp(e["timestamp"]/1000).strftime("%H:%M:%S"),e["action"],e["actor"],json.dumps(e.get("details"))])
        line=re.sub(r"Nouride Home \([^)]*\)","Nouride Home (<mac>)",line)
        print(re.sub(r"\b(actor|user|username)\b","\\1",line))
' "$1" | sed -E "s/ ([a-z0-9._-]+@[a-z0-9.-]+|$(id -un)) / <admin> /g"
