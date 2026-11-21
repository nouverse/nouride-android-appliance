#!/bin/sh
# One line on where the phone is: the top activity, the dashboard's path, and the question if it is up.
. "$HOME/android-toolchain/env.sh"
S=${NOTE12:?set NOTE12 to the adb serial of the phone}; D=$(dirname "$0")
top=$(adb -s "$S" shell dumpsys activity activities | grep -m1 topResumedActivity | grep -o "ui\.[A-Za-z]*")
path=""; [ "$top" = "ui.DashboardActivity" ] && path=$(node "$D/wv.mjs" 'location.pathname' 2>/dev/null)
q=$(sh "$D/ui-texts.sh" | grep -c "Go back to Nouride Home?")
echo "$top $path question=$q"
