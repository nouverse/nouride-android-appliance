#!/bin/sh
# On the Mac: a call to Nouride Home Dev's own Nouride (127.0.0.1:18264) with its control token, read from the install
# and never printed. Usage: nr-ctl.sh GET|POST|DELETE <path under /api/control> [json body]
set -eu
DATA="$HOME/Library/Application Support/tech.nouverse.nouride.home.dev"
TOKEN_FILE="$DATA/nouride/.nouride/data/.control-token"
method=$1 path=$2 body=${3:-}
if [ -n "$body" ]; then
  curl -sS -m 180 -X "$method" -H "x-nouride-token: $(cat "$TOKEN_FILE")" -H "Content-Type: application/json" -d "$body" "http://127.0.0.1:18264/api/control$path"
else
  curl -sS -m 180 -X "$method" -H "x-nouride-token: $(cat "$TOKEN_FILE")" "http://127.0.0.1:18264/api/control$path"
fi
echo
