#!/bin/bash
# Prints the phone's audit entries after a millisecond timestamp, oldest first. Usage: audit-since.sh <ms>
bash "$(dirname "$0")/phone-ctl.sh" GET '/audit?limit=30' | python3 -c '
import json,sys,datetime
since=int(sys.argv[1])
for e in reversed(json.load(sys.stdin)["entries"]):
    if e["timestamp"]>since: print(datetime.datetime.fromtimestamp(e["timestamp"]/1000).strftime("%H:%M:%S"),e["action"],e["actor"],json.dumps(e["details"]))
' "$1"
