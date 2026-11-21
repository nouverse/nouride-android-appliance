# On the Mac (sh): deeper pages, the header's Back (nouridehome://back), and Go back.
cd ~/nix-work/nouride-home
. scripts/e2e-mac.sh
D="$HOME/Library/Application Support/tech.nouverse.nouride.home.dev"
show() { e2e dump >/dev/null; python3 -c '
import json,os
s=json.load(open(os.path.expanduser("~/Library/Application Support/tech.nouverse.nouride.home.dev/e2e/state.json")))
w=s["windows"]; d=w["dashboard"]; m=w["main"]
print("  dashboard:", None if d is None else {"visible":d["visible"],"url":d["url"]}, "| main:", None if m is None else {"visible":m["visible"]}, "| question:", s.get("question"))'; }
js() { e2e dashboard-js "$1" >/dev/null; sleep "${2:-2}"; }
echo "# 2. deeper: clicks on the sidebar's Agents, then Chats, then Work"
js 'document.querySelector("a[href=\"/dashboard/agents\"]").click()' 2
js 'document.querySelector("a[href=\"/dashboard/chat\"]").click()' 2
js 'document.querySelector("a[href=\"/dashboard/work\"]").click()' 2
show
echo "# 3. a click on the header's Back (nouridehome://back; in the page but not drawn at this width)"
js 'document.querySelector("a[href=\"nouridehome://back\"]").click()' 3
show
echo "# again"
js 'document.querySelector("a[href=\"nouridehome://back\"]").click()' 3
show
echo "# 4. \"Back to Nouride Home\" here, two pages in"
js 'document.querySelector("a[href=\"nouridehome://home\"]").click()' 3
show
echo "# Stay"; e2e sheet Stay >/dev/null; sleep 2; show
echo "# 5. the header's Back twice more: to the first page, then at the first page"
js 'document.querySelector("a[href=\"nouridehome://back\"]").click()' 3
show
js 'document.querySelector("a[href=\"nouridehome://back\"]").click()' 3
show
echo "# Stay"; e2e sheet Stay >/dev/null; sleep 2; show
echo "# 6. \"Back to Nouride Home\", then Go back"
js 'document.querySelector("a[href=\"nouridehome://home\"]").click()' 3
show
e2e sheet "Go back" >/dev/null; sleep 3; show
grep "way back" "$D"/logs/app.log | tail -12
