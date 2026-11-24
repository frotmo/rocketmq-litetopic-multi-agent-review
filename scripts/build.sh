#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
source "$ROOT/scripts/app-env.sh"

MVN="${MVN:-mvn}"
if ! command -v "$MVN" >/dev/null 2>&1; then
  echo "Maven not found. Install Maven 3.9+ or set MVN to its executable." >&2
  exit 1
fi

"$MVN" -q -DskipTests package
echo "Build OK: $ROOT/target/rocketmq-multiagent-review-1.0.0.jar"