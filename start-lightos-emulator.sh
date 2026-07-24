#!/usr/bin/env bash
#
# Start the LightOS emulator (lp3aosp AVD) to pilot the Swedish keyboard.
# Usage: ./start-lightos-emulator.sh [avd-name]   (default: lp3aosp)
#
set -u

# --- toolchain paths (override by exporting before running) ---
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/opt/jdk-17* 2>/dev/null | head -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

AVD="${1:-lp3aosp}"
LOG="/tmp/${AVD}-emulator.log"

if [ ! -d "$ANDROID_AVD_HOME/$AVD.avd" ]; then
  echo "AVD '$AVD' not found in $ANDROID_AVD_HOME. See docs/LIGHTOS-EMULATOR.md to create it." >&2
  exit 1
fi

# --- start (or reuse) the emulator ---
if adb devices 2>/dev/null | grep -qw "device"; then
  echo "An emulator is already running — reusing it."
else
  echo "Starting $AVD (writable-system, windowed)…  logs: $LOG"
  # -writable-system is REQUIRED every boot so the LightOS system app works.
  nohup emulator -avd "$AVD" -writable-system -no-snapshot -gpu swiftshader_indirect \
    > "$LOG" 2>&1 &
  disown
  adb wait-for-device
  printf "Booting"
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    printf "."; sleep 2
  done
  echo " ready."
fi

# keep the Android HOME button on the normal launcher (LightOS is not a launcher)
adb shell cmd package set-home-activity com.android.launcher3/.uioverrides.QuickstepLauncher >/dev/null 2>&1

# bring LightOS to the foreground
adb shell am start -n com.thelightphone.sdk.emulator/.MainActivity >/dev/null 2>&1

cat <<'EOF'

LightOS is running (Swedish keyboard build).

  Show our tool in the LightOS home:
     Settings → "Allowed Tools" → choose "All Tools", then open UI Demo.

  …or jump straight to the keyboard screen:
     adb shell am start -n com.thelightphone.uidemo/com.thelightphone.sdk.LightActivity
     then tap TEXT INPUT → the field.

  Stop the emulator when done:
     adb emu kill
EOF
