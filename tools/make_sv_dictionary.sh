#!/usr/bin/env bash
# Regenerates app/src/main/assets/sv_words.txt from the HeliBoard experimental
# Swedish wordlist (Leipzig Corpora Collection, CC BY 4.0). Requires network
# access to codeberg.org. Output: "word<TAB>f<TAB>offensive" per line, f = AOSP
# log-scale frequency 0-255, cut at f>=63 (~114k entries, ~1.6 MB).
set -euo pipefail
cd "$(dirname "$0")/.."
out="app/src/main/assets/sv_words.txt"; mkdir -p "$(dirname "$out")"
curl -L -o /tmp/main_sv.combined \
  "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists_experimental/main_sv.combined"
grep '^ word=' /tmp/main_sv.combined | sed 's/^ word=//' | awk -F',' '{
  w=$1; f=""; off="0"
  for(i=2;i<=NF;i++){ if($i ~ /^f=/) f=substr($i,3)
                      if($i ~ /^possibly_offensive=true/) off="1" }
  if (f>=63) print w"\t"f"\t"off }' > "$out"
wc -l "$out"
