#!/bin/sh
# On the Mac: asks the Home Dev dashboard window where it is and who /auth/me says is signed in, through the e2e hooks
# (`dashboard-js` writes the answer into the address, `dump` reads it back). Usage: mac-probe.sh [hook...]
# With arguments, runs that hook first (e.g. open-dashboard) and waits.
set -eu
B="$HOME/nix-work/nouride-home/target/debug/bundle/macos/Nouride Home Dev.app/Contents/MacOS/nouride-home"
D="$HOME/Library/Application Support/tech.nouverse.nouride.home.dev"
if [ $# -gt 0 ]; then "$B" --e2e "$@"; sleep 6; fi
"$B" --e2e dashboard-js 'fetch("/api/control/auth/me").then(async r=>{const j=await r.json().catch(()=>({}));return {me:r.status,user:(j.user&&j.user.username)||null,mustChange:j.user?j.user.mustChangePassword:null};}).catch(e=>({me:"fetch failed"})).then(a=>{a.href=location.href;a.hidden=document.documentElement.style.visibility==="hidden";a.h1=(document.querySelector("h1,h2")||{}).textContent||null;history.replaceState(null,"",location.pathname+location.hash.split("?")[0]+"?e2e="+encodeURIComponent(JSON.stringify(a)));})'
sleep 3
"$B" --e2e dump
sleep 2
python3 - "$D/e2e/state.json" <<'PY'
import json, sys, re, urllib.parse
s = json.load(open(sys.argv[1]))
text = json.dumps(s)
m = re.search(r'e2e=([^"&]+)', text)
print(re.sub(r"[\w.+-]+@[\w.-]+", "<admin>", urllib.parse.unquote(m.group(1))) if m else "no probe answer; windows: " + json.dumps(s.get("windows")))
PY
