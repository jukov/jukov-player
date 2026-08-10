#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/publish-internal.sh VERSION_CODE VERSION_NAME

Publishes a signed, minified App Bundle to Google Play Internal Testing.

Authentication (choose one):
  1. Save the service-account key as ./play-service-account.json
  2. Set GOOGLE_PLAY_SERVICE_ACCOUNT_FILE to its path
  3. Set ANDROID_PUBLISHER_CREDENTIALS to the JSON contents

Example:
  ./scripts/publish-internal.sh 2 1.0.1
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 2 ]]; then
  usage >&2
  exit 2
fi

version_code="$1"
version_name="$2"

if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
  echo "VERSION_CODE must be a positive integer." >&2
  exit 2
fi

if [[ -z "$version_name" ]]; then
  echo "VERSION_NAME must not be empty." >&2
  exit 2
fi

if [[ -z "${ANDROID_PUBLISHER_CREDENTIALS:-}" ]]; then
  credentials_file="${GOOGLE_PLAY_SERVICE_ACCOUNT_FILE:-play-service-account.json}"
  if [[ ! -f "$credentials_file" ]]; then
    echo "Google Play credentials not found at: $credentials_file" >&2
    echo "See --help for supported authentication options." >&2
    exit 1
  fi
  ANDROID_PUBLISHER_CREDENTIALS="$(<"$credentials_file")"
  export ANDROID_PUBLISHER_CREDENTIALS
fi

if [[ ! -f keystore.properties ]] && [[ -z "${JUKOV_RELEASE_STORE_FILE:-}" ]]; then
  echo "Release signing is not configured." >&2
  echo "Create keystore.properties or set the JUKOV_RELEASE_* variables." >&2
  exit 1
fi

export JUKOV_VERSION_CODE="$version_code"
export JUKOV_VERSION_NAME="$version_name"

echo "Publishing info.jukov.player $version_name ($version_code) to Internal Testing..."
./gradlew --stacktrace :androidApp:publishReleaseBundle
