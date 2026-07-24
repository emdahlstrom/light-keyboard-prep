# Running the keyboard inside the LightOS emulator

The [`app`-module IME](EMULATOR.md) validates the layout on plain Android. To see the
keyboard the way LightOS actually renders it — *embedded* in a tool via
`LightTextInputEditor`, inside the LightOS shell — use Light's LightOS emulator
(`light-sdk`'s `sdk/emulator`, run as a system app). Reference:
<https://github.com/lightphone/light-sdk/tree/main/docs/system_app>.

## Key finding: the SDK hardcodes English for embedded text input

`sdk/ui/.../LightTextInputEditor.kt` builds an **`EnQwertyLp3KeyboardViewModel`**
(lines 68 & 216). There is no layout selection on the embed path, so a tool's text
field is always English QWERTY — even once LightOS ships the open-source keyboard.
Making a merged layout appear in tools needs a one-line SDK change
(`EnQwerty…` → the desired `…ViewModel`, or a real layout lookup). This is worth
raising with Light separately from the layout PR.

## What we did to show Swedish

1. **Publish our keyboard to mavenLocal** (avoids the GitHub Packages auth the SDK
   build needs, and injects `SvQwerty`):
   ```bash
   # in light-keyboard-prep, on sv-qwerty:
   ./gradlew :ui:publishToMavenLocal -PprojectVersion=0.0.16
   ```
2. **Point light-sdk at mavenLocal** — add `mavenLocal()` as the first repo in
   `settings.gradle.kts` › `dependencyResolutionManagement.repositories`.
3. **Patch the hardcoded layout** in `sdk/ui/.../LightTextInputEditor.kt`:
   `sed -i 's/EnQwertyLp3KeyboardViewModel/SvQwertyLp3KeyboardViewModel/g'`.
4. Follow `docs/system_app` for the rest: AOSP **test-keys** image
   (`system-images;android-34;default;x86_64` — google_apis is `dev-keys` and will
   NOT accept the platform key), `hw.lcd 1080x1240 @ 480`, generate `platform.jks`
   from the AOSP test keys, `./gradlew :sdk:emulator:assembleDebug`, boot with
   `-writable-system`, `adb root && adb remount` (`adb disable-verity && adb reboot`
   if needed), push to `/system/priv-app/LightOSEmulator/`, reboot, verify
   `uid=1000`.
5. Build + install a tool with a text field:
   `./gradlew :examples:ui-demo:assembleDebug && adb install -r …/ui-demo-debug.apk`.
6. Launch → open **UI Demo → TEXT INPUT** → tap the field → the Swedish keyboard
   renders in the LightOS shell (`docs/screenshots/lightos-swedish-keyboard.png`).

## Piloting it (windowed)

The `lp3aosp` AVD persists the installed emulator + tool. To drive it yourself:

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_AVD_HOME=/home/em/.config/.android/avd
# -writable-system is required on EVERY boot (per Light's docs)
~/android-sdk/emulator/emulator -avd lp3aosp -writable-system &
adb wait-for-device
# jump straight to the Swedish keyboard:
adb shell am start -n com.thelightphone.uidemo/com.thelightphone.sdk.LightActivity
# then tap TEXT INPUT and the field, or navigate the LightOS UI
```

> Because of the hardcoded-EnQwerty finding above, the Swedish keyboard here depends
> on the local SDK patch (step 3). Stock LightOS would show English until Light wires
> layout selection into `LightTextInputEditor`.
