# On the Mac (sh): the new Home Dev, the dashboard of its own Nouride 0.8.41, and "Back to Nouride Home" on the first page.
cd ~/nix-work/nouride-home
. scripts/e2e-mac.sh
D="$HOME/Library/Application Support/tech.nouverse.nouride.home.dev"
show() { e2e dump >/dev/null; python3 -c '
import json,os,sys,re
s=json.load(open(os.path.expanduser("~/Library/Application Support/tech.nouverse.nouride.home.dev/e2e/state.json")))
w=s["windows"]; d=w["dashboard"]; m=w["main"]
print("  dashboard:", None if d is None else {"visible":d["visible"],"url":d["url"]}, "| main:", None if m is None else {"visible":m["visible"]}, "| question:", s.get("question"))'; }
js() { e2e dashboard-js "$1" >/dev/null; sleep "${2:-2}"; }
e2e_open
echo "# the app opened"; show
e2e open-dashboard >/dev/null; sleep 8
echo "# Open Nouride"; show
js 'history.replaceState(null,"",location.pathname+"?e2e="+encodeURIComponent(JSON.stringify({mark:(navigator.userAgent.match(/NourideHome\/\d+/)||[])[0],width:innerWidth,links:[...document.querySelectorAll("a[href^=\"nouridehome:\"]")].map(a=>[a.getAttribute("href"),a.getAttribute("aria-label")||a.textContent.trim(),!!(a.offsetWidth||a.offsetHeight)])})))'
echo "# what the page says of itself (written into its address)"; show
js 'history.replaceState(null,"",location.pathname)' 1
echo "# 1. a click on the sidebar's \"Back to Nouride Home\" (nouridehome://home), first page"
js 'document.querySelector("a[href=\"nouridehome://home\"]").click()' 3
show
echo "# Stay"; e2e sheet Stay; sleep 2; show
grep "way back" "$D"/logs/*.log 2>/dev/null | tail -4 || grep -rl "way back" "$D" 2>/dev/null | head -3
