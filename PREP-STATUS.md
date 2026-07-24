# Prep status

Reconstructed 2026-07-24 into this repo (the work originated in an unrelated docs
repo and was moved here). `main` is a pristine clone of upstream `lightphone/light-keyboard`
at `v0.0.16` (`59b305f`); every branch below stacks on that.

## Branch map

```
main (v0.0.16, pristine upstream)
└── sv-qwerty ......... add swedish qwerty layout        → first PR
    ├── da-qwerty ..... add danish qwerty layout
    ├── fi-qwerty ..... add finnish qwerty layout
    ├── no-qwerty ..... add norwegian qwerty layout
    ├── is-qwerty ..... add icelandic qwerty layout (+ eth/thorn/y-acute long-press floor)
    ├── sme-qwerty .... add northern sami qwerty layout   (Divvun-mirrored)
    ├── sv-screenshots  paparazzi render tests
    └── sv-app-extras   offline autocorrect + dictation   (sideload only)
```
Each layout branch = `sv-qwerty` + exactly one commit. The five language branches
are independent siblings (all based on `sv-qwerty`), so they rebase cleanly once
`sv-qwerty` merges upstream.

## Layout facts (research-verified — see docs/)

| Lang | Locale | Row 1 | Row 2 | Row 3 | Notes |
|---|---|---|---|---|---|
| sv | `sv` | `qwertyuiopå` | `asdfghjklöä` | `zxcvbnm` | national standard |
| fi | `fi` | `qwertyuiopå` | `asdfghjklöä` | `zxcvbnm` | rows = Swedish; ä is 5.2% of Finnish letters |
| da | `da` | `qwertyuiopå` | `asdfghjkl`**`æø`** | `zxcvbnm` | æ **before** ø |
| no | `no` | `qwertyuiopå` | `asdfghjkl`**`øæ`** | `zxcvbnm` | ø **before** æ; `no` covers Bokmål+Nynorsk |
| is | `is` | `qwertyuiop`**`ð`** | `asdfghjkl`**`æö`** | `zxcvbnm`**`þ`** | 11/11/8; branch also adds d→ð, t→þ, y→ý to the shared long-press map |
| sme | `se` | `ášertyuiopŋ` | `asdfghjklđŧ` | `žzčcvbnm` | Divvun layout; á/š/č take q/w/x, which return via long-press |

Labels use upstream's English-name pattern (`"QWERTY (Swedish)"` …). Whether to use
endonyms (`Svenska`, `Íslenska`, `Davvisámegiella`) is an open question to raise in the issue.

## What is verified vs. pending

**Verified here:** patches apply cleanly onto `v0.0.16`; all branches present with
correct rows/locales/registry wiring; brace balance; no stray identifiers; `sv-app-extras`
has zero `Log` calls and Vosk via the version catalog (matching upstream conventions).

**NOT done here — no Android toolchain on this machine:**
- **No build/test run.** Upstream's `./gradlew check` (JDK 17) has not been run against
  these branches on this machine. It *was* green on the original machine, and the code is
  byte-identical, but re-run it before opening any PR.
- **Screenshot PNGs not regenerated.** `sv-screenshots` holds the Paparazzi *rig* only.
  Run `./gradlew :ui:recordPaparazziDebug` to produce the images. `docs/screenshots/` holds
  schematic SVG-based mockups (fine for an issue's Mocks section, but not the device renders).
- **Dictionary asset not fetched.** `sv-app-extras` expects `app/src/main/assets/sv_words.txt`
  (~1.6 MB, CC BY 4.0, not committed). Generate it with `tools/make_sv_dictionary.sh`
  (needs codeberg.org access). Until then autocorrect stays inert (no crash — it just skips).

## To turn this into PRs

1. Create your fork of `lightphone/light-keyboard` on GitHub; add it as a remote and push
   these branches (`sv-qwerty` first, e.g. as `<you>--add-swedish-layout`).
2. Run `./gradlew check` on `sv-qwerty`; regenerate screenshots.
3. File the issue **"Layout request: Swedish (QWERTY)"** — sections: About / Mocks / Example
   code / Path forward (mirror the merged Colemak issue #3 → PR #4). Human-written; a Swedish
   original alongside the English is explicitly welcomed by their AI policy.
4. If the issue is quiet ~2 days, open a **draft** PR and ask for feedback — that's how Colemak
   got its green light. Don't mark ready until a Light dev engages.
5. Offer the other Nordic layouts one at a time, only once invited. Keep autocorrect/dictation
   out of layout PRs (a new dependency is an explicit rejection category) — raise them later as
   a Discussion framed around extending LightOS's existing English spell-check.

Full playbook, conventions profile, and per-decision sources: `docs/light-conventions-nordic-prep.md`.
