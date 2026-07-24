---
title: "Light conventions & the Nordic layout family: fork prep"
description: Light Phone code conventions and contribution playbook, plus the prepared Danish, Finnish, Norwegian, Icelandic and Northern Sámi layout branches.
---

Prepared 2026-07-23. Companion to [Swedish text entry on the Light Phone III](/research/lightphone-swedish-keyboard/). Everything below is staged in a local clone, push-ready the moment the GitHub fork of `lightphone/light-keyboard` exists.

## The branch map (what to push to the fork)

All branches build and pass tests; all commits are authored as you, styled to the house voice (lowercase imperative subjects, no bodies — Light merge-commits PRs, so these live in `main`'s history verbatim).

| Branch | Contents | Purpose |
|---|---|---|
| `sv-qwerty` | `add swedish qwerty layout` — layout only, zero API changes | **The first PR** (push as `emdahlstrom--add-swedish-layout` per community precedent) |
| `da-qwerty` | Danish layout on top of sv-qwerty | Follow-up PR when invited |
| `fi-qwerty` | Finnish layout (rows identical to Swedish) | Follow-up |
| `no-qwerty` | Norwegian layout (serves Bokmål *and* Nynorsk) | Follow-up |
| `is-qwerty` | Icelandic layout + the ð/þ/ý long-press floor fix | Follow-up (strongest correctness case) |
| `sme-qwerty` | Northern Sámi layout (Divvun-mirrored) + additive long-press entries | Follow-up (goodwill/visibility case) |
| `sv-app-extras` | autocorrect commit + dictation commit, app module only | The sideload build; **never** part of a layout PR |
| `sv-screenshots` | Paparazzi screenshot tests | Renders the "mocks" for issues; keeps the test plugin out of PRs |

Layout follow-up branches are each based on `sv-qwerty` (they need its row-width mechanism) and rebase cleanly onto `main` once the Swedish PR merges.

## The layout family (research-verified)

Every layout below was verified against AOSP LatinIME source (byte-level for da/nb/sv/fi), iOS, Gboard, and the national physical standards. All share the 11-key/32dp row mechanism from the Swedish patch.

| Language | Enum | Locale | Rows | The one critical detail |
|---|---|---|---|---|
| Swedish | `SvQwerty` | `sv` | `qwertyuiopå / asdfghjklöä / zxcvbnm` | national standard, = Finnish |
| Finnish | `FiQwerty` | `fi` | identical to Swedish | ä alone is **5.2%** of Finnish letters (vowel harmony) — the strongest frequency case; š/ž belong on long-press of s/z (official loanword orthography) |
| Danish | `DaQwerty` | `da` | `… / asdfghjklæø / …` | **æ before ø** — the reverse is a Norwegian keyboard, instantly resented |
| Norwegian | `NoQwerty` | `no` | `… / asdfghjkløæ / …` | **ø before æ**; locale is macrolanguage `no` — CLDR/ICU made it the canonical parent of Bokmål (`nb`) and Nynorsk (`nn`), and both standards share one keyboard (AOSP ships only `nb`, iOS/Gboard ship two dictionary entries over one layout) |
| Icelandic | `IsQwerty` | `is` | `qwertyuiopð / asdfghjklæö / zxcvbnmþ` (11/11/8) | ð (4.4% of letters!), þ and ý are **absent from the open-source keyboard's long-press map** (source-verified: no d/t entries at all, y→ÿ only) — and the stock closed keyboard shows the same gap fingerprint on-device (a Czech user's missing-letters report, incl. ý, matches the map's gaps exactly; ð/þ specifically unverified on-device). Practical upshot: paste-relay is the only likely entry path for ð/þ/ý today. Layout is byte-identical to iOS/FlorisBoard; row 3 fits: 49+8×32+49 = 354dp. The branch also adds the 3-entry `EnShared` floor fix (d→ð, t→þ, y→ý) |
| Northern Sámi | `SmeQwerty` | `se` | `ášertyuiopŋ / asdfghjklđŧ / žzčcvbnm` (11/11/8) | Mirrors the MIT-licensed Divvun/UiT mobile layout: all 7 Sámi letters are dedicated keys; á/š/č sit where q/w/x were, and the evicted q/w/x return via additive long-press entries (collision-free — no other layout has those key codes). ISO: `se` is the 639-1 code (BCP-47 canonical); `sme` kept in the class name for greppability against GiellaLT repos |

Sámi scope decisions from the research: Northern Sámi first (~90% of all Sámi speakers, spans NO/SE/FI, and its Divvun layout drops onto the LP3 grid unchanged). Lule/Southern Sámi are trivial Nordic variants for later; Inari needs one placement decision; **Skolt cannot fit** (needs 4 letter rows). On a locked-down phone there is no Divvun-app escape hatch — if the built-in keyboard lacks č, the phone cannot write Northern Sámi, which is the whole pitch.

Labels follow upstream's English-name pattern (`"QWERTY (English)"` → `"QWERTY (Swedish)"` etc.). Endonym labels ("Svenska", "Íslenska", "Davvisámegiella") are defensible UX — flag as an open question in the issue rather than deviating silently. Locales use `Locale.forLanguageTag(...)` (avoids the constructor deprecation).

## Light's code conventions (profiled from upstream)

What the profiler found in `lightphone/light-keyboard@main`, now applied to every branch:

- **Comments:** ~6/100 lines in hard files, near-zero in boilerplate; no file headers, no `@param` tags; KDoc only on library-facing abstractions; terse, informal, "why"-only comments (typos survive review; dev-history like "tried comet effect but…" is welcome). LLM-verbose comments are explicitly called out in their CONTRIBUTING. Our additions were 2–4× over house density — all slimmed.
- **Zero `Log` calls** — there's a dedicated upstream commit `remove logs`. All our logging removed.
- **Naming:** `SCREAMING_SNAKE` + unit suffix (`_DP`, `_MS`); layout objects `XxQwerty` with nested case-variant objects; VM files `XxQwertyViewModel.kt` containing `XxQwertyLp3KeyboardViewModel`; test names as lowercase backtick sentences.
- **Structure:** single-name imports, ASCII-sorted; ~100-char soft line limit; version catalog for every dependency (no string coordinates); no ktlint/detekt — style is enforced socially, `./gradlew check` runs stock lint + unit tests (CI on JDK 17).
- **Commits:** lowercase, imperative, subject-only (`bump to 0.0.16`, `remap external keys for lightos`). Merge commits preserve contributor commits on `main` forever.

## The contribution playbook (from the merged Colemak PR, forensically)

1. **Issue first** — title `Layout request: Swedish (QWERTY)`, four sections like the Colemak request: *About* (adoption evidence), *Mocks* (the Paparazzi renders), *Example code* (a snippet following `EnQwerty.kt`), *Path forward* (offer to implement, ask for the green light). Human-written; their AI policy welcomes a Swedish original alongside the English.
2. **The green light in practice:** the Colemak issue got zero comments — engagement happened on a **draft PR** opened two days later. If the issue sits silent a couple of days, open a draft PR and ask for initial feedback; don't mark ready-for-review until a Light dev engages. Expect ~1-day review turnarounds once engaged; total issue→merge was 6 days.
3. **What reviewers did:** asked design questions and deferred to data-backed answers (an SMS-frequency analysis got cited in the merged code's KDoc); requested keeping `main`'s default layout untouched; light style nits; merged warmly ("thrilled to have an alt layout (and hopefully more soon)").
4. **Hard rules:** PR must map to a green-lit issue (else closed); `./gradlew check` green; no public API changes, no new third-party deps, no architecture changes; crediting sources is a Code-of-Conduct requirement.
5. **The cautionary tale:** the Zhuyin request arrived as a giant bundle (layout + engine + LGPL dictionary) and has sat unanswered — scope small, always. This is why autocorrect (asset + engine) and dictation (Vosk = forbidden new dependency) stay out of every layout PR and get raised later as separate conversations, autocorrect framed around extending their existing English spell-check.
6. **Attribution house style:** no NOTICE file upstream; credits live as bare-link KDoc/`//` comments. Our `NOTICE.md` (Divvun MIT, KEZO/Type MIT, Leipzig CC BY 4.0, Vosk Apache-2.0) rides only on `sv-app-extras` — and the right move upstream is to *ask* which mechanism they prefer, which has precedent.

## Cleanups applied in the nativization pass

- All 32 deviations from the convention audit fixed: comment density/KDoc-on-privates, import order, `Locale` API, label pattern, semicolon-joined lines unfolded, thread idiom unified, triple-space nit, Vosk moved into the version catalog, four repeated attribution sentences reduced to one bare link + NOTICE.
- The dictation retry loop's `decorView.postDelayed` hack replaced with a main-thread `Handler`.
- Dictionary regeneration scripted (`tools/make_sv_dictionary.sh`) instead of docs-only.
- Commits reworded to house style and re-verified: build green, all tests pass on every branch.

## Sources per decision

**Layout rows (all languages).** AOSP LatinIME is the primary source — its `nordic` rowset is literally QWERTY + appended keys, filled per locale; fetched and byte-verified from source: [method.xml](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/java/res/xml/method.xml) (locale→rowset table: `sv/fi/da/nb: nordic`, `is: qwerty`) · [values-sv](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/tools/make-keyboard-text/res/values-sv/donottranslate-more-keys.xml) (å/ö/ä) · [values-da](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/tools/make-keyboard-text/res/values-da/donottranslate-more-keys.xml) (å/æ/ø) · [values-nb](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/tools/make-keyboard-text/res/values-nb/donottranslate-more-keys.xml) (å/ø/æ — the Danish↔Norwegian swap is source-verified) · [values-fi](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/tools/make-keyboard-text/res/values-fi/donottranslate-more-keys.xml) (identical keyspecs to sv; š/ž promoted on s/z) · [values-is](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/tools/make-keyboard-text/res/values-is/donottranslate-more-keys.xml) (d→ð, t→þ, acute-first popups). Physical standards: [kbdlayout.info KBDDA](http://kbdlayout.info/KBDDA/) (Danish), [KBDNO](http://kbdlayout.info/KBDNO/) (Norwegian/NS 4131), [KBDFI](http://kbdlayout.info/KBDFI/), [Icelandic ÍST 125](https://en.wikipedia.org/wiki/Icelandic_keyboard_layout), [SFS 5966](https://jkorpela.fi/sfs5966.html) (joint Finnish–Swedish standard by design). Cross-vendor: [HeliBoard locale extras sv/fi/is](https://github.com/Helium314/HeliBoard) · [FlorisBoard icelandic.json](https://github.com/florisboard/florisboard) (11/11/8 Icelandic) · [jokull/LyklabordApp](https://github.com/jokull/LyklabordApp) (iOS Icelandic rows recreated by an Icelandic developer) · [CLDR is-t-k0-android.xml](https://raw.githubusercontent.com/unicode-org/cldr/release-38-1/keyboards/android/is-t-k0-android.xml) (Gboard Icelandic harvested by Unicode).

**32dp uniform pitch.** AOSP sizes nordic keys at 100%/11 per row (rows_nordic.xml above); iOS shrinks Swedish keys the same ~9% ([ACKeyboard measurements](https://github.com/acoomans/ACKeyboard), [zoul/ios-keyboards](https://github.com/zoul/ios-keyboards)); upheld unanimously by the three-judge adversarial panel (see the Swedish study's adversarial section).

**Locale codes.** Norwegian macrolanguage `no`: [ICU/CLDR v39 Norwegian locale changes](https://icu.unicode.org/design/norwegian-locales-changes-in-v39) (made `no` the canonical parent of `nb`/`nn`), [JDK-6888129](https://bugs.openjdk.java.net/browse/JDK-6888129) (historic Java `nb` fragility), AOSP ships only `nb` among 249 subtypes (method.xml) while iOS/Gboard/SwiftKey offer both written standards over one layout ([SwiftKey language list](https://support.microsoft.com/en-us/swiftkey-keyboard/what-languages-are-currently-supported-for-microsoft-swiftkey-keyboard)). Northern Sámi `se`: ISO 639-1 two-letter code exists, and BCP-47 mandates the shortest subtag; CLDR data lives at `se`; Divvun's own layout files are `se.yaml`/`se-NO.yaml` etc. inside the `sme.kbdgen` bundle ([giellalt/keyboard-sme](https://github.com/giellalt/keyboard-sme)). `da`/`fi`/`is`/`sv` are uncontroversial 639-1. `Locale.forLanguageTag` over the constructor: JDK deprecation of `Locale(String)` (JDK 19+).

**Sámi layout + licensing.** Rows quoted verbatim from [giellalt/keyboard-sme `se.yaml`](https://github.com/giellalt/keyboard-sme) (android primary layer: `á š e r t y u i o p ŋ / a s d f g h j k l đ ŧ / ž z č c v b n m`); MIT license verified (© 2019 UiT The Arctic University of Norway); shipped as [Divvun Keyboards on Google Play](https://play.google.com/store/apps/details?id=no.uit.giella.keyboards.Sami) / [App Store](https://apps.apple.com/us/app/divvun-keyboards/id948386025). Language prioritization (Northern ≈ 90% of Sámi speakers; Skolt needs 4 rows → deferred): [Northern Sámi, Wikipedia](https://en.wikipedia.org/wiki/Northern_Sami), per-language letters from the GiellaLT keyboard repos (keyboard-smj/-sma/-smn/-sms). The additive long-press feasibility (á/š/č→q/w/x collision-free; per-layout maps blocked by private `heldKeys`/`setLayout` and the hardcoded `is EnShared.ExtendedCharKeyboard` check) was verified directly in `EnBaseViewModel.kt`/`EnShared.kt`.

**Letter frequencies** (dedicated-key justifications): [sttmedia Swedish](https://www.sttmedia.com/characterfrequency-swedish) / [Finnish](https://www.sttmedia.com/characterfrequency-finnish) (ä = 5.21%, 9th most frequent) / [Danish](https://www.sttmedia.com/characterfrequency-danish) / [Norwegian](https://www.sttmedia.com/characterfrequency-norwegian) / [Icelandic](https://www.sttmedia.com/characterfrequency-icelandic); [practicalcryptography Icelandic](http://practicalcryptography.com/cryptanalysis/letter-frequencies-various-languages/icelandic-letter-frequencies/) (ð = 4.39%, 10th most frequent letter) and Danish; Finnish vowel harmony and š/ž orthography: [Korpela, Letters in Finnish](https://jkorpela.fi/lang/finnish-letters.html); Norwegian accent needs (é, ò, ô): [Språkrådet, Aksentteikn](https://sprakradet.no/godt-og-korrekt-sprak/rettskriving-og-grammatikk/tegn/aksentteikn/).

**The Icelandic ð/þ/ý gap.** Source-verified against upstream `EnShared.extendedCharMapping` at `lightphone/light-keyboard@59b305f`: entries exist only for a c e i l n o s u y z — no `d`, no `t`; `y` offers only ÿ. For the *stock closed* keyboard (not yet the open-source one), the evidence is inferential but strong: a Czech LP3 owner's on-device missing-letters report (ě ř ť ů ď ň ý — [r/LightPhone, May 2025](https://www.reddit.com/r/LightPhone/comments/1kl0xo9/my_7_days_light_journey/)) matches the open-source map's gaps letter-for-letter, and the LP2-era long-press sets reported for a/o match the map's a/o entries — so the two inventories almost certainly coincide. ý is directly attested missing on-device; ð/þ are unverified on-device (no Icelandic LP3 report exists) but their host keys have no long-press entries at all. Caveat kept honest: Icelandic text can still be *entered* via the dashboard Notes copy→paste relay, and displays fine when received — the gap is about typing. Worth one direct on-device long-press check of d/t/y before citing in the upstream issue.

**Conventions profile.** Derived entirely from `git show main:` content at `lightphone/light-keyboard@59b305f` (comment density counted per file; zero-`Log` policy from upstream commit `a22863e remove logs`; commit style from `git log` — e.g. `bump to 0.0.16`, `remap external keys for lightos`; merge-not-squash from PR merge commits #1–#11).

**Contribution playbook.** [CONTRIBUTING.md](https://github.com/lightphone/light-keyboard/blob/main/CONTRIBUTING.md) (issue-first, green light, `./gradlew check`, no-new-deps, AI policy verbatim) · [CODE_OF_CONDUCT.md](https://github.com/lightphone/light-keyboard/blob/main/CODE_OF_CONDUCT.md) (crediting sources is mandatory) · [issue #3, Layout request: Colemak](https://github.com/lightphone/light-keyboard/issues/3) (the request template; zero on-issue comments) · [PR #4](https://github.com/lightphone/light-keyboard/pull/4) (draft-first flow, review exchanges, data-backed deference, warm merge, contributor commits preserved on main) · [issue #5](https://github.com/lightphone/light-keyboard/issues/5) (the unanswered big-bundle cautionary tale) · [org discussion #70](https://github.com/lightphone/light-sdk/discussions/70) (reduced responsiveness; priorities) · `.github/workflows/pr-check.yml` (CI = `gradlew check` on JDK 17).

**Autocorrect/dictation quarantine from PRs.** "No additional third-party dependencies" (CONTRIBUTING, above) rules out the Vosk dependency; the asset/licensing question format follows issue #5's precedent of asking rather than bundling. Engine and data provenance: [KEZO555/Type](https://github.com/KEZO555/Type) (MIT, © Adam Weber) · [Helium314/aosp-dictionaries](https://codeberg.org/Helium314/aosp-dictionaries) (Swedish wordlist, Leipzig Corpora CC BY 4.0 — cite Goldhahn, Eckart & Quasthoff, LREC 2012) · [Vosk](https://alphacephei.com/vosk/) (Apache 2.0) and [vosk-model-small-sv-rhasspy-0.15](https://alphacephei.com/vosk/models) (MIT).

## Remaining human steps

Tracked in [issue #2](https://github.com/emdahlstrom/plummis-doc/issues/2): create the GitHub fork, push the branches, file the Swedish layout request in your own words, and follow the draft-PR flow. The Nordic-family table above is the "hopefully more soon" answer ready to go the moment they ask.
