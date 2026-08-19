#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <build-number> <marketing-version> [--upload]" >&2
}

if [[ "$#" -lt 2 || "$#" -gt 3 ]]; then
  usage
  exit 2
fi

build_number="$1"
marketing_version="$2"
mode="${3:---export}"

if [[ ! "$build_number" =~ ^[1-9][0-9]*$ ]]; then
  echo "Build number must be a positive integer." >&2
  exit 2
fi

if [[ ! "$marketing_version" =~ ^[0-9]+(\.[0-9]+){1,2}$ ]]; then
  echo "Marketing version must contain two or three numeric components, for example 1.0 or 1.0.1." >&2
  exit 2
fi

if [[ "$mode" != "--export" && "$mode" != "--upload" ]]; then
  usage
  exit 2
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "App Store publishing requires macOS and Xcode." >&2
  exit 1
fi

if ! xcodebuild -version >/dev/null 2>&1; then
  echo "A full Xcode installation must be selected with xcode-select." >&2
  exit 1
fi

if ! security find-identity -v -p codesigning | grep -q "Apple Distribution"; then
  echo "No Apple Distribution identity with a private key was found in the keychain." >&2
  echo "Install the certificate created from this Mac's CSR before publishing." >&2
  exit 1
fi

output_root=".context/ios-release/${marketing_version}-${build_number}"
archive_path="${output_root}/Jukovplayer.xcarchive"
export_path="${output_root}/export"
export_options="iosApp/Configuration/AppStoreExportOptions.plist"

if [[ "$mode" == "--upload" ]]; then
  export_options="iosApp/Configuration/AppStoreUploadOptions.plist"
fi

mkdir -p "$output_root"

xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath "$archive_path" \
  -allowProvisioningUpdates \
  CURRENT_PROJECT_VERSION="$build_number" \
  MARKETING_VERSION="$marketing_version" \
  clean archive

xcodebuild \
  -exportArchive \
  -archivePath "$archive_path" \
  -exportPath "$export_path" \
  -exportOptionsPlist "$export_options" \
  -allowProvisioningUpdates

if [[ "$mode" == "--upload" ]]; then
  echo "Build ${marketing_version} (${build_number}) uploaded to App Store Connect."
else
  echo "App Store package exported to ${export_path}."
fi
