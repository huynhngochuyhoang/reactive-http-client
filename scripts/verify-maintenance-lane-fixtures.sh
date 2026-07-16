#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

expect_failure() {
  local expected="$1"
  shift
  local output
  if output="$("$ROOT_DIR/scripts/verify-maintenance-lane.sh" "$@" 2>&1)"; then
    echo "Maintenance-lane fixture unexpectedly passed: $*" >&2
    exit 1
  fi
  grep -q "$expected" <<<"$output" || {
    echo "Maintenance-lane fixture did not report '$expected': $output" >&2
    exit 1
  }
}

expect_failure "latest immutable 2.x release tag" v3.0.0 2.14.0
expect_failure "must differ from maintenance release" v2.14.1 2.14.1
expect_failure "must use published predecessor 2.14.0" v2.14.1 2.13.0

echo "Maintenance-lane guard fixtures passed."
