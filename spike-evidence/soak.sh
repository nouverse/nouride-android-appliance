#!/usr/bin/env bash
# From this box: asks the phone's daemon for /health every INTERVAL seconds for HOURS hours and
# writes one line per ask: UTC time, HTTP code and time, status/version/uptime, then the phone's
# screen state, deep Doze state, battery level and charger, read over adb.
set -u
URL="${1:?url}"; HOST=10.18.1.99; PORT="${2:?adb port}"; OUT="${3:?log}"; HOURS="${4:-10}"; INTERVAL="${5:-300}"
end=$(( $(date +%s) + HOURS * 3600 ))
rescan() { seq 30000 49999 | xargs -P 256 -I{} timeout 0.4 bash -c "echo > /dev/tcp/$HOST/{} 2>/dev/null && echo {}" 2>/dev/null | head -1; }
while [ "$(date +%s)" -lt "$end" ]; do
  ts=$(date -u +%FT%TZ)
  raw=$(curl -sS -m 15 -w '\n%{http_code} %{time_total}s' "$URL" 2>&1)
  meta=$(printf '%s\n' "$raw" | tail -n 1)
  health=$(printf '%s\n' "$raw" | head -n -1 | python3 -I -c '
import json, sys
text = sys.stdin.read()
try:
    d = json.loads(text)
    print("status=%s version=%s uptime=%ss" % (d.get("status"), d.get("version"), d.get("uptime")))
except Exception:
    print("body=%r" % text[:100])')
  if ! adb -s "$HOST:$PORT" get-state > /dev/null 2>&1; then
    adb connect "$HOST:$PORT" > /dev/null 2>&1
    if ! adb -s "$HOST:$PORT" get-state > /dev/null 2>&1; then
      p=$(rescan); [ -n "$p" ] && PORT=$p && adb connect "$HOST:$PORT" > /dev/null 2>&1
    fi
  fi
  phone=$(adb -s "$HOST:$PORT" shell 'dumpsys power | grep -o "mWakefulness=[A-Za-z]*"; echo "deep=$(dumpsys deviceidle get deep)"; dumpsys battery | grep -E "^  (level|AC powered|USB powered):" | tr -d " "' 2>&1 | tr '\n' ' ')
  echo "$ts http=$meta $health | adb=$PORT $phone" >> "$OUT"
  sleep "$INTERVAL"
done
echo "$(date -u +%FT%TZ) soak logger ended" >> "$OUT"
