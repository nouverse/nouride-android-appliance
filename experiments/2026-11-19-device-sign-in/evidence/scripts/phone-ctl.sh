#!/bin/sh
# A call to Nouride Home Dev's own Nouride on the Note 12 (forwarded to 127.0.0.1:28264) with its control token, read
# with run-as and never printed. Usage: phone-ctl.sh GET|POST|DELETE <path under /api/control> [json body]
set -eu
. "$HOME/work/android-toolchain/env.sh"
S=${NOTE12:?the phone's adb serial, e.g. 192.168.0.12:40000}
token=$(adb -s "$S" shell run-as tech.nouverse.nouride.home.dev cat files/debian/rootfs/srv/nouride/.nouride/data/.control-token | tr -d '\r\n')
method=$1 path=$2 body=${3:-}
if [ -n "$body" ]; then
  curl -sS -m 180 -X "$method" -H "x-nouride-token: $token" -H "Content-Type: application/json" -d "$body" "http://127.0.0.1:28264/api/control$path"
else
  curl -sS -m 180 -X "$method" -H "x-nouride-token: $token" "http://127.0.0.1:28264/api/control$path"
fi
echo
