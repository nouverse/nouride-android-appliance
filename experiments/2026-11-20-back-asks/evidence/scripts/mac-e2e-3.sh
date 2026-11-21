# On the Mac (sh): over the cat the way back leaves with no question; the app's window is made again when it was closed.
cd ~/nix-work/nouride-home
. scripts/e2e-mac.sh
D="$HOME/Library/Application Support/tech.nouverse.nouride.home.dev"
show() { e2e dump >/dev/null; python3 -c '
import json,os
s=json.load(open(os.path.expanduser("~/Library/Application Support/tech.nouverse.nouride.home.dev/e2e/state.json")))
w=s["windows"]; d=w["dashboard"]; m=w["main"]
print("  dashboard:", None if d is None else {"visible":d["visible"],"url":d["url"]}, "| main:", None if m is None else {"visible":m["visible"]}, "| question:", s.get("question"), "| phase:", s["snapshot"].get("phase"))'; }
js() { e2e dashboard-js "$1" >/dev/null; sleep "${2:-2}"; }
echo "# 7. Home Dev's own Nouride turned off, Open Nouride: the cat"
e2e stop >/dev/null; sleep 6
e2e open-dashboard >/dev/null; sleep 5; show
echo "# the app's window closed, then a click on the cat page's \"Back to Nouride Home\" (nouridehome://home)"
e2e close-main >/dev/null; sleep 2; show
js 'document.querySelector("a[href=\"nouridehome://home\"]").click()' 3
show
echo "# 8. Nouride started again"
e2e start >/dev/null
i=0; while [ $i -lt 30 ]; do sleep 3; curl -s -m 3 http://127.0.0.1:18264/health | grep -q '"version":"0.8.41"' && break; i=$((i+1)); done
show
grep "way back" "$D"/logs/app.log | wc -l
echo "# the app's window closed again, as it was before this run; the app stays in the menu bar"
e2e close-main >/dev/null; sleep 2; show
pgrep -fl "Nouride Home Dev.app/Contents/MacOS/nouride-home" | sed 's#/Users/[^/]*#~#'
TMPDIR=$HOME/nix-work/back-asks sh scripts/mac-guard.sh check
curl -s -m 4 http://127.0.0.1:18254/health | grep -o '"version":"[^"]*"' | sed 's/^/the owner'"'"'s own Nouride, read only: /'
