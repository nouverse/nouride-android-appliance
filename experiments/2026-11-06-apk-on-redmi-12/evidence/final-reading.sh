#!/usr/bin/env bash
# Takes the closing soak reading: /health and /dashboard/ from this box over the LAN, Android's view of the
# app, and copies of the app's logs. Usage: final-reading.sh <adb port> <evidence dir>
set -u
HOST=10.18.1.99; PORT="${1:?adb port}"; EV="${2:?evidence dir}"; PKG=tech.nouverse.nouride.android
TS=$(date -u +%Y%m%dT%H%MZ)
{
  echo "== $(date -u +%FT%TZ) from $(hostname) (LAN)"
  echo "GET http://$HOST:18264/health"
  curl -sS -m 15 -w '\nHTTP %{http_code} in %{time_total}s\n' "http://$HOST:18264/health"
  curl -sS -m 15 -o /tmp/dash.$$ -w 'GET /dashboard/ HTTP %{http_code}, %{size_download} bytes, %{content_type}, %{time_total}s\n' "http://$HOST:18264/dashboard/"
  grep -o '<title>[^<]*</title>' /tmp/dash.$$; rm -f /tmp/dash.$$
} > "$EV/lan-T1.txt" 2>&1
adb connect "$HOST:$PORT" > /dev/null 2>&1
adb -s "$HOST:$PORT" shell "
echo \"== \$(date)\"
dumpsys activity services $PKG | grep -E 'ServiceRecord|isForeground|createTime|restartTime|startRequested'
echo '== proc'; dumpsys activity processes $PKG | grep -E '\*APP\*|oom adj|curProcState'
echo \"== standby bucket: \$(am get-standby-bucket $PKG)\"
echo '== deviceidle whitelist:'; dumpsys deviceidle whitelist | grep nouride
echo \"== deep=\$(dumpsys deviceidle get deep) light=\$(dumpsys deviceidle get light)\"
echo \"== \$(dumpsys power | grep -o 'mWakefulness=[A-Za-z]*')\"
echo '== wake locks'; dumpsys power | grep -E \"PARTIAL_WAKE_LOCK.*nouride\"
echo '== wifi locks'; dumpsys wifi | grep -E 'WifiLock\{nouride'
echo '== battery'; dumpsys battery | grep -E 'AC powered|USB powered|status|level|temperature'
echo '== app processes'; ps -A -o USER,PID,PPID,RSS,NAME | grep \"\$(ps -A -o USER,NAME | grep ' $PKG\$' | awk '{print \$1}')\"
" > "$EV/app/android-state-$TS.txt" 2>&1
for f in health service daemon install; do
  adb -s "$HOST:$PORT" exec-out run-as $PKG cat files/logs/$f.log > "$EV/app/$f-$TS.log" 2>&1
done
echo "$TS"
