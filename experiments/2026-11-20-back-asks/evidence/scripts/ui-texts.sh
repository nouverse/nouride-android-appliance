#!/bin/sh
# The texts on screen that belong to Home Dev (a dialog's title, message and buttons), from a uiautomator dump; every
# other package's nodes are dropped unread.
. "$HOME/android-toolchain/env.sh"
S=${NOTE12:?set NOTE12 to the adb serial of the phone}
adb -s "$S" shell 'uiautomator dump /sdcard/nrh-ui.xml >/dev/null 2>&1; cat /sdcard/nrh-ui.xml; rm -f /sdcard/nrh-ui.xml' | python3 -I -c '
import sys, re
x = sys.stdin.read()
for n in re.findall(r"<node [^>]*>", x):
    if "package=\"tech.nouverse.nouride.home.dev\"" not in n: continue
    t = re.search(r" text=\"([^\"]*)\"", n); c = re.search(r" class=\"([^\"]*)\"", n); b = re.search(r" bounds=\"([^\"]*)\"", n)
    if t and t.group(1): print(c.group(1).split(".")[-1], "|", t.group(1), "|", b.group(1))
'
