#!/usr/bin/env bash
# Start an Android 14 emulator on the GitHub runner, logging every step to ci-out/ so a
# failure can be read from the android-ci branch (the Actions log is not reachable from here).
set -x
OUT=${OUT:-ci-out}
mkdir -p "$OUT"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/emulator:$SDK/platform-tools:$PATH"
IMG="system-images;android-34;google_apis;x86_64"
# avdmanager and the emulator must look in the same place (run 2: "Unknown AVD name [ci]").
export ANDROID_USER_HOME="$HOME/.android"
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"
yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager --install "platform-tools" "emulator" "$IMG" > "$OUT/sdkmanager.txt" 2>&1 || { echo "sdkmanager failed"; exit 1; }
echo no | avdmanager create avd -n ci -k "$IMG" -d pixel_6 --force || exit 1
# The runner has ~6 GB free; the default phone wants 7.4 GB of user data (run 3). 2 GB is plenty here.
CFG="$ANDROID_AVD_HOME/ci.avd/config.ini"
sed -i '/^disk.dataPartition.size/d' "$CFG" && echo "disk.dataPartition.size=2048M" >> "$CFG"
df -h "$HOME" | tail -1
emulator -list-avds
ls -la "$ANDROID_AVD_HOME"
ls -l /dev/kvm || true
nohup emulator -avd ci -partition-size 2048 -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot -camera-back none \
  > "$OUT/emulator-console.txt" 2>&1 &
timeout 300 adb wait-for-device || { echo "no device after 300 s"; tail -50 "$OUT/emulator-console.txt"; exit 1; }
for i in $(seq 1 120); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && { echo "booted after $((i*5)) s"; break; }
  sleep 5
done
[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "emulator did not boot"; exit 1; }
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82
