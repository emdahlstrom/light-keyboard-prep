# Running the keyboard in an emulator at LP3 resolution

There is no official Light Phone AVD. The LP3 screen is **1080 × 1240 px at 480 dpi**
(≈3.92", giving a logical **360 × 413 dp** — the same config the Paparazzi tests use).
That 360 dp width is why 11-key rows drop to 32 dp (11 × 32 = 352 ≤ 360).

## One-time setup

```bash
export JAVA_HOME=~/opt/jdk-17.0.19+10          # or your JDK 17
export ANDROID_HOME=~/android-sdk
export ANDROID_SDK_ROOT=~/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# SDK packages (once)
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
           "emulator" "system-images;android-34;google_apis;x86_64"

# create an AVD, then stamp it with the LP3 hardware profile
avdmanager create avd -n lp3 -k "system-images;android-34;google_apis;x86_64"
cat >> "$ANDROID_AVD_HOME/lp3.avd/config.ini" <<'EOF'
hw.lcd.width=1080
hw.lcd.height=1240
hw.lcd.density=480
hw.keyboard=no
hw.mainKeys=no
EOF
```

(`ANDROID_AVD_HOME` defaults to `~/.android/avd`, or `~/.config/.android/avd` on some setups —
check where `avdmanager list avd` reports the `.avd` path.)

## Run it

```bash
# interactive window (type on it yourself):
emulator -avd lp3

# headless (drive via adb / screenshots):
emulator -avd lp3 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
```

## Install our keyboard and pick a layout

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

adb shell ime enable com.thelightphone.lp3keyboard/.IMEService
adb shell ime set    com.thelightphone.lp3keyboard/.IMEService

# choose the layout without tapping the UI (uniqueId = "<locale>_<variant>"):
#   sv_qwerty · en_qwerty · en_colemak · da_qwerty · fi_qwerty · no_qwerty · is_qwerty · se_qwerty
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n  <string name="active_layout_id">sv_qwerty</string>\n</map>\n' \
 | adb shell 'run-as com.thelightphone.lp3keyboard sh -c "mkdir -p shared_prefs && cat > shared_prefs/lp3_keyboard_prefs.xml"'
```

Open the **LP3 Keyboard** app (it has a "Try here" field), or focus any text field.
`adb exec-out screencap -p > shot.png` captures the screen.

Gotcha: `am force-stop`-ing the keyboard package drops it as the active IME — re-run
`ime set` afterwards.
