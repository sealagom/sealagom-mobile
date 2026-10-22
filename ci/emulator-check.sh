#!/usr/bin/env bash
# Runs inside the Android emulator job: install the app, open it online, then offline,
# and keep screenshots and the app's log for review (pushed to the android-ci branch).
set -u
OUT=${OUT:-ci-out}
APK=mobile/android/app/build/outputs/apk/debug/app-debug.apk
PKG=com.sealagom.app
mkdir -p "$OUT/screens"
shot() { adb exec-out screencap -p > "$OUT/screens/$1.png" || true; }

adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 45
shot 01-home-online
# Scroll once so the screenshot shows more of the page.
adb shell input swipe 540 1600 540 600 400; sleep 4; shot 02-home-scrolled
# Open the menu (top bar) and the Warnings group if visible.
adb shell input tap 540 140; sleep 3; shot 03-tap-top
adb logcat -d -s chromium Capacitor Console SystemWebViewClient AndroidRuntime > "$OUT/logcat-online.txt" || true

# Offline: what a ship without a connection sees.
adb shell svc wifi disable; adb shell svc data disable; sleep 3
adb shell am force-stop $PKG; sleep 2
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 20
shot 04-offline-start
adb shell input swipe 540 1600 540 600 400; sleep 3; shot 05-offline-scrolled
adb logcat -d -s chromium Capacitor Console SystemWebViewClient AndroidRuntime > "$OUT/logcat-offline.txt" || true
adb shell svc wifi enable; adb shell svc data enable
adb shell dumpsys package $PKG | grep -E "versionCode|versionName|targetSdk" > "$OUT/package.txt" || true
echo "emulator check done"
