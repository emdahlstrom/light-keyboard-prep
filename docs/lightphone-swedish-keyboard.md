---
title: Swedish text entry on the Light Phone III
description: Deep investigation into making Swedish (å ä ö) text entry bearable on the Light Phone III — keyboard, voice-to-text, sideloading, and a working prototype.
---

Investigation dates: 2026-07-22 (deep pass with parallel research agents; initial pass same day)

## Problem

Typing Swedish (å ä ö Å Ä Ö) on the Light Phone III is painfully slow. Goal: **anything that makes Swedish text entry bearable**, ideally something that works on an actual retail LP3 soon.

## TL;DR — the truth, verified at code level

| Fact | Status |
|---|---|
| Stock keyboard long-press has å ä ö | ✅ Works today (slowly) — `EnShared.kt` extended-char map includes å ä ö and uppercase |
| LP3 voice-to-text | ❌ English-only, hard-locked server-side (cloud Rev.ai); Rev.ai *streaming* doesn't even offer Swedish |
| Sideload a Swedish keyboard into **Messages** | ❌ Impossible today — LightOS Messages draws its **own in-process keyboard**, ignores Android IMEs |
| Swedish layout in the open-source keyboard | ✅ **Prototype built and tested in this session** (patch in this repo); upstream contribution is invited |
| SDK texting tool | ❌ No SMS APIs, no share/intent bridge; clipboard→LightOS paste demonstrably broken (their issue #85) |
| SDK dictation tool | ⏳ Becomes feasible when Light's audio PR (#119, `LightAudioCapture`) + `RECORD_AUDIO` grant land |
| Full Swedish texting via the Android layer | ⚠️ Possible **today** via unofficial modding route (Swedish IME + open-source SMS app), with real caveats |

## What exists today (all verified)

### 1. The stock keyboard: å ä ö via long-press

The LP3 keyboard's long-press does not show a small popup — it swaps the whole keyboard to an extended-character layout for one keystroke. The mapping (in the open-source keyboard, `EnShared.kt`) already contains **å and ä under `a`, ö under `o`**, and the uppercase forms under `A`/`O` when shifted. So Swedish is *typeable* today: long-press → visual swap → tap → auto-revert. Roughly 3–4× the effort per Swedish character, ~10% of Swedish text. That's the pain being solved.

### 2. Voice-to-text: English-only, and structurally stuck

LP3 has dictation (Messages compose screen; Settings → Preferences → Phone → Messages → "Enable Voice to Text"). It is **cloud transcription by Rev.ai** over cell data, and Light's docs state plainly: *"Voice-to-Text is limited to the English language."* Two structural blockers for Swedish:

- The limit is server-side (Light's backend → Rev.ai) — nothing on the device can change it.
- Rev.ai's **streaming** API supports ~9 languages, **not Swedish** (Swedish exists only in their async batch API). Even if Light wanted Swedish dictation tomorrow, they'd need a different vendor or pipeline.

Precedent is discouraging: LP2 got the same English-only Rev.ai feature in 2021 and it never gained a second language. **Verdict: dead end near-term, unless done as a community SDK tool (see below).**

### 3. The keyboard is open source — and Swedish is a wanted contribution

[`lightphone/light-keyboard`](https://github.com/lightphone/light-keyboard) (MIT, Kotlin/Compose) is the LP3 keyboard: *"To be used in LightOS, community tools, and/or as an Android system keyboard."* Current layouts: English QWERTY + Colemak only; the Colemak layout was a **merged community PR** (their PR #4), which is the exact template for a Swedish contribution. Process (their CONTRIBUTING.md): open a feature-request issue ("This includes new languages/layouts!"), wait for green light, then PR; **all repo communication must be human-written** (they explicitly reject LLM-generated text), so the issue/PR must be filed personally.

**Crucial timing caveat:** as of July 2026 LightOS does *not* yet embed this keyboard — Messages still uses Light's older closed keyboard UI ("Eventually, we will replace the custom UI in LightOS with this" — `Lp3KeyboardWrapper.kt`). A merged Swedish layout reaches Messages only when Light flips that switch ("Coming soon!" per their README). It reaches **SDK tools immediately** (tools embed this library in-process).

### 4. What we built in this session: working Swedish layout

A complete `sv_qwerty` layout for `light-keyboard` v0.0.16 — **compiled, unit-tested (all green), APK built**:

![Proposed Swedish QWERTY layout](../../../assets/sv-qwerty-layout.svg)

- Rows `qwertyuiopå` / `asdfghjklöä` / `zxcvbnm` — the real Swedish layout, å ä ö as first-class keys, no long-press needed.
- 11-key rows don't fit at the stock 35dp key width (360dp screen), so keys narrow to 32dp on those rows (11 × 32 = 352dp), and after adversarial review all three letter rows share the same 32dp pitch (see below).
- Registry entry `SvQwerty(Locale("sv"), "qwerty", "QWERTY (Svenska)")` — appears automatically in the keyboard app's layout picker.
- Swipe-capture config includes å ä ö (the +32 lowercase offset conveniently holds for Å/Ä/Ö) — ready for when Light's swipe decoder ships, though Swedish swipe would also need a Swedish lexicon on their closed decoder side.
- Uppercase Å Ä Ö work through the normal shift/caps flow (layouts are literal strings — no case-mapping machinery).
- Tests mirror their existing viewmodel tests, plus å/ä/ö commit checks.

The full change is **[`patches/light-keyboard-sv-qwerty.patch`](https://github.com/emdahlstrom/plummis-doc/blob/claude/lightphone-swedish-keyboard-hyqii8/patches/light-keyboard-sv-qwerty.patch)** in this repo. Apply with `git am` on `light-keyboard@59b305f`.

Note: there is no autocorrect/prediction/dictionary anywhere in the keyboard — nothing English-biased to fight, but also no Swedish autocorrect to add yet.

### Adversarial validation: 13 agents tried to beat this design — it held

A structured red-team pass (2 researchers → 3 adversarial attackers → 5 independent designers → 3-judge panel) tried to disprove the 11-key/32dp solution and design something better. Outcome:

**The research settled it: 11/11/7 with dedicated å ä ö is the universal convention.** AOSP's `nordic` layout is literally QWERTY rows with å/ö/ä appended (Swedish = Finnish; Danish/Norwegian swap the last two keys — exactly the family argument for the upstream issue). iOS has done it since 2009; Gboard, SwiftKey, and Samsung follow; every vendor accepts the ~9% key shrink with no other compensation; even Nordic Nokia keypads printed å ä ö on keys 2 and 6. A keyboard hiding å ä ö behind long-press is below the feature-phone baseline Swedish users left in the 1990s. HeliBoard's architecture is a bonus insight for Light: it defines no "Swedish layout" at all, just per-locale *extra keys* appended to any base layout — worth mentioning upstream as an alternative mechanism.

**Three attackers failed to break it.** The one real cost, quantified: 32dp keys (~5.8mm) raise per-tap miss probability ~30% relative on a device with no autocorrect to absorb errors — but that width is exactly what iPhone and Pixel Swedish keyboards use, and the alternative (long-press) swaps the entire keyboard every 4–5 words of Swedish (å ä ö ≈ 4.5% of letters; "på" alone ≈2% of words). The strongest rival — a tuned long-press with å ä ö promoted to first position — delivers ~40% of the benefit at higher API cost. One genuine hazard surfaced for the PR notes: the layout's 29-letter swipe config will eventually meet Light's closed, English-only swipe decoder — swipe enablement is host-controlled, so Light just shouldn't enable Swedish swipe until their decoder has a Swedish lexicon.

**Four of five independent designers converged on the identical rows.** (The fifth — a gesture-flick design with å ä ö as key-flicks — scored last with all three judges: novel mechanism, zero muscle-memory transfer, low merge odds.)

**The judges' unanimous winner refined rather than replaced the design:** uniform 32dp key pitch across *all three* letter rows (not just the 11-key ones), so the grid keeps one rhythm — matching how Gboard/iOS/AOSP shrink all Swedish rows uniformly. Adopted: the third row now renders at the same pitch via layout-local composition (public API only, precedent in the repo's own `EnShared`).

### 5. The hard truth about installing it on a retail LP3

Verified from Light's own repos and staff statements (their discussions #70, #92, #93):

- **LightOS Messages does not use the Android IME framework.** It renders its own keyboard in-process (LightOS core UI is a React Native launcher). Sideloading + enabling our keyboard IME (`adb shell ime set …`) affects only Android-layer text fields — **not Messages, and not SDK tools either** (tools embed the keyboard as a library, pinned by Light's build).
- The dashboard "Developer Mode" toggle is **not** Android developer mode — a Light engineer called it "a placeholder"; it only selects the tool-trust policy (Light-approved / SDK-built / any APK).
- **No official sideload path exists.** Light's README: sideloading via ADB is fine "if you're already comfortable" but current retail LightOS builds are "not yet ready to play nice" with SDK tools. Tool installs via the Light dashboard are rolling out (first signed tools — Weather, Authenticator — shipped with LightOS v568, July 2026); the community Tool Library is targeted ~October 2026, and a wifi File Manager for local installs is announced.
- **Unofficial route (community modding, unsupported):** the LP3 boots into its underlying Android layer via a key sequence at power-on; from there, standard Android developer options + USB debugging can be enabled, and sideloaded APKs **do appear in the LightOS toolbox** (confirmed by a user in Light's own discussion #93; Light staff acknowledge such methods exist, don't block them, but "reserve the right" to remove them). LightOS updates may reset Android-layer changes. Exact key sequence is low-confidence community lore — the modding channel on Light's Discord is the reliable source.

## The options, ranked for "something working on my phone"

### Option A — today, zero risk, stock phone: three real improvements

1. **Switch to the QWERTZ layout** (Settings → Preferences → General → Keyboard): it has **dedicated ä and ö keys** — only å still needs a long-press on `a`. This alone removes ~two-thirds of the pain. (An early LP3 bug rendered the rightmost QWERTZ keys off-screen; fixed in LightOS v510, June 2025.)
2. **Dashboard Notes relay for longer messages** — officially documented by Light: type with your real Swedish keyboard on a computer in the [Light dashboard's Notes tool](https://support.thelightphone.com/hc/en-us/articles/8418578128404-Notes-Tool), it syncs to the phone, and *"you can also Copy text based notes to paste into text messages on the phone."* Copy/paste in all keyboards was enabled in v550 and improved in v558. There is even an [unofficial dashboard API/CLI](https://github.com/garado/light) that can push notes programmatically — an automatable Swedish-text pipe to the phone.
3. Long-press remains for å (and everything on QWERTY): long-press `a` → å/ä, `o` → ö, uppercase via shift first.

Community context: this pain is well documented on r/LightPhone — Swedish users report long-press "makes typing even slower," a Norwegian user cancelled his LP3 pre-order over the missing layout, and Light (Joe Hollier, Feb 2025) has said more Latin-script layouts are the "easier" part of a language expansion they want to do but won't put a timeline on.

### Option B — today, unofficial: full Swedish texting via the Android layer ⚠️

The one path to **proper Swedish texting on your physical LP3 this week**, at the cost of stepping outside LightOS:

1. Boot into the LP3's Android layer (community method — get the current sequence from the modding channel on Light's official Discord; low-confidence public write-ups exist).
2. Enable Developer options + USB debugging.
3. `adb install` **our Swedish keyboard APK** and enable it: `adb shell ime enable com.thelightphone.lp3keyboard/.IMEService && adb shell ime set com.thelightphone.lp3keyboard/.IMEService`.
4. `adb install` an open-source, GMS-free SMS app (e.g. Fossify Messages or QUIK) — the Android layer has full telephony, and sideloaded apps show up in the LightOS toolbox.
5. Text in Swedish, with real å ä ö keys, in that app.

Alternative IMEs for step 3, if autocorrect matters (our build has none — neither does Light's): the community projects [adam-weber/light-keyboard](https://github.com/adam-weber/light-keyboard) (LP3-look system IME with offline autocorrect and offline **Vosk dictation**) and its fork [KEZO555/Type](https://github.com/KEZO555/Type) (13 languages with per-language layouts, dictionaries, and offline dictation in 9 — **no Swedish yet**, but Vosk publishes a Swedish model, so contributing/forking Swedish support there would yield offline Swedish *dictation* on the Android layer too). There's also a dedicated modding community at r/ModifiedLightPhones with an LP3 guide.

**Caveats, honestly:** unsupported by Light; making the sideloaded app the default SMS handler will divert SMS handling away from LightOS Messages (probably reversible, but it changes how the phone behaves — test before committing); OTA updates may undo Android-layer changes; a future LightOS build could close the modding route. Whether the standard `ime` adb commands behave identically on retail LP3 is unverified (they're stock-Android commands and should).

**Zero-risk rehearsal first:** everything in this path can be tested on a computer — plain Android emulator (API 34, AOSP, 1080×1240), install the keyboard APK, verify the Swedish layout end-to-end. The light-sdk repo also documents a full LightOS emulator setup (`docs/system_app`) for testing against the real LightOS environment.

### Option C — the real fix: upstream the Swedish layout (patch is ready)

1. File "Layout request: Swedish QWERTY" on `light-keyboard` (mirror their merged Colemak request, issue #3 → PR #4). Must be personally written — they reject AI-generated repo communication. Strengthen with: Swedish = Finnish layout exactly; Danish/Norwegian differ by two keycaps; one Nordic layout serves four countries.
2. On green light, submit the prepared patch (adjusting to their review feedback — expect discussion about the 32dp key width).
3. When LightOS adopts the open-source keyboard ("coming soon" per Light), Swedish lands **in Messages itself**, plus every SDK tool.

### Option D — later: Swedish dictation as an SDK tool

Architecturally viable and worth pursuing once two Light-side pieces land (both are in motion):

- **Their PR #119** ("foreground audio") adds `LightAudioCapture` — real-time mic PCM as a Kotlin Flow, exactly what streaming STT needs — and wires `RECORD_AUDIO` into the permission flow. Audio is Light's own "most requested" SDK priority ("we are on it" — staff, discussion #70).
- The `RECORD_AUDIO` runtime grant must join LightOS's default grantable set (currently only CAMERA and READ_MEDIA_AUDIO). Light invites permission requests "when there's a real use case."

Then the tool is: mic → `LightAudioCapture` PCM → HTTPS (OkHttp/Ktor are allow-listed; arbitrary hosts permitted) → Swedish STT (KB-Whisper — the Swedish national library's Whisper models — via any hosted endpoint) → text in a `LightTextInputEditor` → copy to clipboard → paste in Messages. The paste handoff looks viable: Light officially documents copying dashboard Notes into text messages, and v550/v558 brought copy/paste to all keyboard instances — the one caution is that *SDK-tool* clipboard had a paste bug into some LightOS fields (their issue #85, closed) — worth verifying on-device. Fully on-device Swedish Whisper is also conceivable: Light has allow-listed a community JNI/native Maven artifact before (anki-android-backend precedent), so a whisper.cpp wrapper could be requested. A neat UX hook exists: the keyboard's mic key (`SpecialKey.Voice`) fires a callback that tools can intercept for their own dictation flow.

### Ruled out (with proof)

- **SDK texting tool:** no SMS/telephony/contacts service methods; `Intent`/`startActivity`/`getSystemService` are hard-blocked by Light's build plugin; no share bridge. Duplicate of Messages anyway.
- **System-IME replacement of the Messages keyboard:** Messages doesn't consume system IMEs.
- **Stock dictation in Swedish:** server-side English lock + vendor's streaming API lacks Swedish.
- **Bluetooth hardware keyboard:** officially unsupported for text input ([Light's Bluetooth article](https://support.thelightphone.com/hc/en-us/articles/360031126131-Bluetooth): external keyboards pair but only media keys register), and Light staff confirm BT HID input is "currently not hooked up to LightOS' text input" (discussion #92) — though the open-source keyboard just gained hardware-key handling (July 2026 commits), so this may change. Ignore "for Light Phone III" keyboard accessories on Amazon — they won't type.

### Also built: offline Swedish autocorrect (the other half of the pain)

What LightOS itself offers is only a **manual spell-check** ([official docs](https://support.thelightphone.com/hc/en-us/articles/6189805411732-Spell-Check)): an optional setting that underlines misspelled words in Messages; long-pressing an underlined word shows up to three suggestions. It never corrects automatically — and its dictionary is English, so **Swedish texters should probably turn it off** (Settings, optional) to avoid every Swedish word being underlined. The open-source keyboard has no correction anywhere (the "decoder" mentioned above is only for swipe typing). So the sideload build now includes a **prototype offline Swedish autocorrect**, active only when the Swedish layout is selected:

- **Engine:** noisy-channel corrector ported from the MIT-licensed [KEZO555/Type](https://github.com/KEZO555/Type) LP3-community keyboard — generate all 1-edit variants, keep real words, rank by slip plausibility (transposition and *adjacent-key* substitution beat arbitrary edits — o→ö and p→å are neighbours on this layout, which is exactly the fat-finger error the narrow keys invite) then by corpus frequency, with a conservative distance-2 fallback for long words. Measured sub-millisecond per word in benchmarks; corrects on space/punctuation; **one backspace reverts** the correction.
- **Swedish-aware guards:** a word that splits into two dictionary words — including via the linking-s, like *fotbollsplan* — is never "corrected", because Swedish compounds freely and no list contains them all (the failure mode that bites German/Swedish users on other keyboards).
- **Dictionary:** 114,369 modern Swedish word forms with frequencies from the [HeliBoard experimental Swedish dictionary](https://codeberg.org/Helium314/aosp-dictionaries) (Leipzig Corpora Collection, **CC BY 4.0** — license-clean, unlike the CC-BY-SA subtitle lists). Offensive-flagged words are kept (never auto-"fixed" away) but never *suggested*.
- **Where it lives:** entirely in the keyboard's `app` module (the standalone IME) — zero changes to the shared `ui` library, which mirrors Light's own architecture where word intelligence plugs in from outside. That makes it a plausible future upstream conversation, but it ships in our sideload build today.
- 11 unit tests cover the Swedish specifics (adjacency fixes, compound guards, case preservation, offensive gating). Code patch: [`patches/light-keyboard-sv-autocorrect.patch`](https://github.com/emdahlstrom/plummis-doc/blob/claude/lightphone-swedish-keyboard-hyqii8/patches/light-keyboard-sv-autocorrect.patch) (applies on top of the layout patch); the dictionary asset is regenerated with:

```bash
curl -L -o main_sv.combined \
  "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists_experimental/main_sv.combined"
grep '^ word=' main_sv.combined | sed 's/^ word=//' | awk -F',' '{
  w=$1; f=""; off="0"
  for(i=2;i<=NF;i++){ if($i ~ /^f=/) f=substr($i,3)
                      if($i ~ /^possibly_offensive=true/) off="1" }
  if (f>=63) print w"\t"f"\t"off }' > app/src/main/assets/sv_words.txt
```

### And: offline Swedish dictation, on the mic key

The sideload build now also does what Light's own dictation can't — **Swedish voice-to-text, fully on-device**:

- The mic key on the Swedish layout (hidden in the upstream patch, active in this build) toggles dictation via [Vosk](https://alphacephei.com/vosk/) — the same offline engine the community Type keyboard uses on LP3-class hardware. Speak; each pause commits a segment of text; press the mic again to stop. **Audio never leaves the phone.**
- The Swedish model ([`vosk-model-small-sv-rhasspy-0.15`](https://alphacephei.com/vosk/models), MIT) is a one-time **289 MB download** from the keyboard's setup app — nothing is bundled, so the APK stays ~45 MB. Honest caveat: this model's accuracy is unpublished (it comes from the Rhasspy home-automation community); expect it to handle everyday Swedish but test before trusting it for anything nuanced. Output is lowercase and unpunctuated (standard for offline models).
- An IME can't show permission dialogs, so the first mic press pops the mic-permission request via an invisible shim activity; press the mic again after granting. Without the model installed, the mic press opens the setup app's download button instead.
- The upstream patch is untouched by all of this — dictation, like autocorrect, lives entirely in the app module of the sideload build (`patches/light-keyboard-sv-autocorrect.patch` now carries both).

The long game for *proper* Swedish dictation quality remains KB-Whisper via Light's coming SDK audio APIs (their PR #119) — but this works today, offline, in the keyboard itself.

## Recommended sequence

1. **Now:** Switch to QWERTZ for dedicated ä/ö keys and use the dashboard-Notes relay for longer Swedish messages (Option A). If comfortable with the modding route and its caveats, Option B delivers real Swedish texting immediately — rehearse on an emulator first.
2. **This week:** Personally file the Swedish layout request on `light-keyboard` (Option C step 1) — the patch in this repo is ready to submit the moment they say yes. Also worth a 👍/comment on their audio work and a note in discussion #70 that Swedish dictation is a wanted use case for `RECORD_AUDIO`.
3. **Watch for:** LightOS adopting the open-source keyboard (Swedish lands in Messages); the dashboard Tool Library (~Oct 2026); PR #119 merging (dictation tool becomes buildable); their File Manager (no-ADB installs).

## Artifacts from this investigation

- `patches/light-keyboard-sv-qwerty.patch` — complete, tested Swedish layout for light-keyboard v0.0.16
- `src/assets/sv-qwerty-layout.svg` — layout illustration
- Debug APK of the keyboard app with the Swedish layout — built in-session (27 MB; rebuild reproducibly by applying the patch and running `./gradlew :app:assembleDebug`)

## Sources

- [lightphone/light-keyboard](https://github.com/lightphone/light-keyboard) — keyboard source; README ("not yet using this as the embedded keyboard"); `EnShared.kt` (å ä ö in long-press); `Lp3KeyboardWrapper.kt` (LightOS custom UI); CONTRIBUTING.md (layout requests welcome, human-written comms); merged Colemak PR #4
- [lightphone/light-sdk](https://github.com/lightphone/light-sdk) — SDK; `LightSdkPlugin.kt` (allow-list/blocked APIs); `LightServiceMethod.kt` (no SMS); [PR #119 audio](https://github.com/lightphone/light-sdk/pull/119); [issue #85 clipboard](https://github.com/lightphone/light-sdk/issues/85); [discussion #70 priorities](https://github.com/lightphone/light-sdk/discussions/70); [discussion #92 BT keyboards](https://github.com/lightphone/light-sdk/discussions/92); [discussion #93 developer mode](https://github.com/lightphone/light-sdk/discussions/93)
- [Voice-to-Text announcement](https://medium.com/the-light-phone/voice-to-text-feature-2b406ebe78ba) (Rev.ai, English-only) · [Rev.ai languages](https://www.rev.ai/languages) (no Swedish in streaming) · [Light support: Language](https://support.thelightphone.com/hc/en-us/articles/360039844751-Language) · [Light support: Keyboard & Texting](https://support.thelightphone.com/hc/en-us/articles/360030790152-Keyboard-Texting)
- [KB-Whisper (Swedish Whisper models)](https://huggingface.co/KBLab/kb-whisper-small) — for a future dictation tool
- Community sweep: [r/LightPhone Scandinavian letters thread](https://www.reddit.com/r/LightPhone/comments/1ijuqw2/scandinavian_letters_on_light_phone_3/) · [QWERTZ ä/ö keys tip](https://www.reddit.com/r/LightPhone/comments/1ctwjpr/) · [Joe Hollier on language plans, Feb 2025](https://www.reddit.com/r/LightPhone/comments/1ihjx5c/) · [Notes Tool relay (official)](https://support.thelightphone.com/hc/en-us/articles/8418578128404-Notes-Tool) · [Bluetooth keyboards unsupported (official)](https://support.thelightphone.com/hc/en-us/articles/360031126131-Bluetooth) · [adam-weber/light-keyboard](https://github.com/adam-weber/light-keyboard) · [KEZO555/Type](https://github.com/KEZO555/Type) · [garado/awesome-light](https://github.com/garado/awesome-light) · [garado/light dashboard CLI](https://github.com/garado/light) · [r/ModifiedLightPhones LP3 guide](https://www.reddit.com/r/ModifiedLightPhones/comments/1nufbm9/light_phone_iii_modding_guide/)
- Community modding write-ups (low confidence; verify in Light's Discord modding channel before use)
