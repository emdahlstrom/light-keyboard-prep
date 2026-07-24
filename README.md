# light-keyboard-prep

Contribution prep for [`lightphone/light-keyboard`](https://github.com/lightphone/light-keyboard):
Nordic keyboard layouts for the Light Phone III, plus a sideload build with
offline Swedish autocorrect and dictation.

This is a **clone of upstream** (`main` = pristine upstream `v0.0.16`). Each piece
of work is its own branch, ready to become a fork branch and a PR.

See **[PREP-STATUS.md](PREP-STATUS.md)** for the full branch map, what's verified,
what still needs doing, and the exact issue/PR process to follow.
Research and rationale live in **[docs/](docs/)**.

## Branches at a glance

| Branch | What | Destination |
|---|---|---|
| `main` | untouched upstream `v0.0.16` | — |
| `sv-qwerty` | Swedish QWERTY layout, zero public-API changes | first PR |
| `da-qwerty` `fi-qwerty` `no-qwerty` `is-qwerty` `sme-qwerty` | Danish, Finnish, Norwegian, Icelandic, Northern Sámi layouts (each on top of `sv-qwerty`) | follow-up PRs, one at a time |
| `sv-screenshots` | Paparazzi render tests for the Swedish layout | mocks / never PR'd as-is |
| `sv-app-extras` | offline autocorrect + Vosk dictation, app module only | sideload build, **not** a PR (adds a dependency) |

Layouts differ from Swedish only where the language does — e.g. Danish row 2 ends
`…l æ ø`, Norwegian `…l ø æ` (the one detail that must not be swapped).
