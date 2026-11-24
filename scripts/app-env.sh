#!/usr/bin/env bash
set -euo pipefail

is_java17() {
  local bin="$1"
  [[ -x "$bin" ]] || return 1
  "$bin" -version 2>&1 | head -n1 | grep -qE 'version "(17|[2-9][0-9])'
}

JAVA_BIN=""
CANDIDATES=()
if [[ -n "${APP_JAVA_HOME:-}" ]]; then
  CANDIDATES+=("$APP_JAVA_HOME/bin/java")
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  CANDIDATES+=("$JAVA_HOME/bin/java")
fi
if command -v java >/dev/null 2>&1; then
  CANDIDATES+=("$(command -v java)")
fi

for candidate in "${CANDIDATES[@]}"; do
  if is_java17 "$candidate"; then
    JAVA_BIN="$(readlink -f "$candidate")"
    break
  fi
done

if [[ -z "$JAVA_BIN" ]]; then
  echo "This application requires JDK 17+." >&2
  echo "Set APP_JAVA_HOME or JAVA_HOME to a JDK 17+ installation." >&2
  exit 1
fi

export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version >/dev/null 2>&1 || {
  echo "Failed to execute $JAVA_HOME/bin/java" >&2
  exit 1
}