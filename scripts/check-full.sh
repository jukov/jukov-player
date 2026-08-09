#!/usr/bin/env bash
set -euo pipefail

./scripts/check-fast.sh

if [[ "$(uname -s)" == "Darwin" ]]; then
  ./scripts/check-ios.sh
else
  echo "Skipping iOS checks on non-macOS host."
fi
