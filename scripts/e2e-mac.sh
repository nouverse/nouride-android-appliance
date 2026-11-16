#!/bin/sh
# Drives Nouride Home Dev on a Mac over ssh, through its e2e hooks (the Dev build only): each call runs the app's
# binary again with `--e2e <action> [argument]`, the running app does it, and its answer is printed.
#
#   . scripts/e2e-mac.sh
#   e2e_open                   # starts the app (in the person's session, as a click would)
#   e2e dump                   # the state as JSON, also in <data>/e2e/state.json
#   e2e place-here [v0.8.37]   # Run it on this computer (a pinned release for an update test)
#   e2e shot home              # a picture of the app's own window, <data>/e2e/home.png
#   e2e quit
APP="${APP:-$HOME/nix-work/nouride-appliance-desktop/target/debug/bundle/macos/Nouride Home Dev.app}"
DATA="$HOME/Library/Application Support/tech.nouverse.nouride.home.dev"
BIN="$APP/Contents/MacOS/nouride-home"

e2e_open() {
  open "$APP"
  i=0
  while [ $i -lt 30 ] && ! pgrep -fx "$BIN" >/dev/null; do sleep 1; i=$((i + 1)); done
  sleep 2
}

e2e() {
  # Only to an app already running: with none, the binary would start as the app itself (single instance), and a
  # build replaced since would go on answering from memory.
  if ! pgrep -fx "$BIN" >/dev/null; then echo '{"ok":false,"error":"the app is not running"}'; return 1; fi
  rm -f "$DATA/e2e/last.json"
  "$BIN" --e2e "$@" >/dev/null 2>&1
  i=0
  while [ $i -lt 120 ] && [ ! -f "$DATA/e2e/last.json" ]; do sleep 1; i=$((i + 1)); done
  cat "$DATA/e2e/last.json" 2>/dev/null || echo '{"ok":false,"error":"no answer from the app"}'
  echo
}

# Waits until the state's `field` (a jq-free grep on state.json) says `value`, at most `seconds`.
e2e_wait() {
  pattern="$1"; seconds="${2:-180}"; i=0
  while [ $i -lt "$seconds" ]; do
    e2e dump >/dev/null || return 1
    grep -q "$pattern" "$DATA/e2e/state.json" && return 0
    sleep 3; i=$((i + 3))
  done
  return 1
}
