#!/bin/sh
# Before and after every test of Nouride Home Dev on a Mac where someone runs their own Nouride: records the sha256 of
# its LaunchAgent and the PID launchd gives it, and fails when either changed. The Dev build must never touch it.
#
#   scripts/mac-guard.sh record   # before
#   scripts/mac-guard.sh check    # after: exits 1 if the person's Nouride was touched
set -eu
PLIST="$HOME/Library/LaunchAgents/tech.nouverse.nouride.plist"
STATE="${TMPDIR:-/tmp}/nouride-home-guard.txt"

now() {
  sum=$( [ -f "$PLIST" ] && shasum -a 256 "$PLIST" | cut -d' ' -f1 || echo none )
  pid=$(launchctl list tech.nouverse.nouride 2>/dev/null | sed -n 's/.*"PID" = \([0-9]*\);.*/\1/p')
  echo "plist=$sum pid=${pid:-none}"
}

case "${1:-}" in
  record) now > "$STATE"; echo "recorded: $(cat "$STATE")" ;;
  check)
    before=$(cat "$STATE"); after=$(now)
    if [ "$before" = "$after" ]; then echo "unchanged: $after"; else echo "CHANGED: before $before, after $after" >&2; exit 1; fi ;;
  *) echo "usage: $0 record|check" >&2; exit 2 ;;
esac
