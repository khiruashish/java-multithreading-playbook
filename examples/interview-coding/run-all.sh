#!/usr/bin/env bash
# Runs every interview solution; each program self-checks and prints PASS. Requires JDK 21+.
set -euo pipefail
cd "$(dirname "$0")"
fail=0
for f in *.java; do
  if out=$(timeout 180 java "$f" 2>&1) && grep -q '^PASS$' <<<"$out"; then
    echo "PASS  ${f%.java}"
  else
    echo "FAIL  ${f%.java}"; echo "$out" | tail -5; fail=1
  fi
done
exit $fail
