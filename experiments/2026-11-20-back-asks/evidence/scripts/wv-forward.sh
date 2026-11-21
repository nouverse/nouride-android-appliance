#!/bin/sh
# Forwards the Home Dev WebView's DevTools socket on the Note 12 to 127.0.0.1:29222.
. "$HOME/android-toolchain/env.sh"
S=${NOTE12:?set NOTE12 to the adb serial of the phone}
pid=$(adb -s "$S" shell pidof tech.nouverse.nouride.home.dev | tr -d '\r')
adb -s "$S" forward --remove tcp:29222 2>/dev/null
adb -s "$S" forward tcp:29222 "localabstract:webview_devtools_remote_$pid"
