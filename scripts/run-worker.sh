#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
source "$ROOT/scripts/app-env.sh"

ROLE="${1:-}"
if [[ -z "$ROLE" ]]; then
  echo "Usage: $0 <style|logic|security|summary> [extra options]" >&2
  exit 1
fi
shift

if [[ ! -d target/classes || ! -d target/lib ]]; then
  ./scripts/build.sh
fi

exec java -cp "target/classes:target/lib/*" edu.lab.review.ReviewLabApplication --role "$ROLE" "$@"