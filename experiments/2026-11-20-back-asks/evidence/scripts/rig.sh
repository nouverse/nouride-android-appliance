#!/bin/sh
# The throwaway Nouride for remote mode (a folder of its own), given a Nouride 0.8.41 binary, with
# a fresh admin whose password is made here and kept only in /tmp/nrh-rig/pw, a fake provider and a Cloudflare quick
# tunnel. Stays in the foreground until killed.
set -eu
R="${RIG:?set RIG to the folder of the throwaway Nouride}"
cd "$R"
for v in $(env | grep -o '^NOURIDE[A-Z_]*'); do unset "$v"; done
cp "${NOURIDE_0841:?set NOURIDE_0841 to a Nouride 0.8.41 binary}" ./nouride
./nouride --version
./nouride users reset > users-reset-back.log 2>&1 || { tail -3 users-reset-back.log; exit 1; }
umask 077; mkdir -p /tmp/nrh-rig; head -c 18 /dev/urandom | base64 | tr -d '/+=' > /tmp/nrh-rig/pw; umask 002
python3 -I fake/models.py &
INITIAL_ADMIN_USERNAME=tester INITIAL_ADMIN_PASSWORD="$(cat /tmp/nrh-rig/pw)" ./nouride start > start-back.log 2>&1 &
CF=$HOME/.local/opt/cloudflared; [ -d "$CF" ] && CF="$CF/cloudflared"
"$CF" tunnel --url http://127.0.0.1:18592 > tunnel-back.log 2>&1 &
trap 'kill $(jobs -p) 2>/dev/null' TERM INT
wait
