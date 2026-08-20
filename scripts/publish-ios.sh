#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
cd "$project_root"

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

profile_name="Jukovplayer App Store"
profile_team_id="UDTBP44Q7F"
profile_app_id="${profile_team_id}.com.nberezovskii.jukovplayer"
profile_found=false
shopt -s nullglob
profiles=(
  "$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"/*.mobileprovision
  "$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"/*.provisionprofile
  "$HOME/Library/MobileDevice/Provisioning Profiles"/*.mobileprovision
  "$HOME/Library/MobileDevice/Provisioning Profiles"/*.provisionprofile
)
shopt -u nullglob

for profile in "${profiles[@]}"; do
  installed_name="$(security cms -D -i "$profile" 2>/dev/null | plutil -extract Name raw -o - - 2>/dev/null || true)"
  installed_team_id="$(security cms -D -i "$profile" 2>/dev/null | plutil -extract TeamIdentifier.0 raw -o - - 2>/dev/null || true)"
  installed_app_id="$(security cms -D -i "$profile" 2>/dev/null | plutil -extract Entitlements.application-identifier raw -o - - 2>/dev/null || true)"
  if [[ "$installed_name" == "$profile_name" &&
        "$installed_team_id" == "$profile_team_id" &&
        "$installed_app_id" == "$profile_app_id" ]]; then
    profile_found=true
    break
  fi
done

if [[ "$profile_found" != true ]]; then
  echo "Provisioning profile '$profile_name' was not found for $profile_app_id." >&2
  echo "Install the App Store provisioning profile supplied by the team Account Holder or Admin." >&2
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
  CURRENT_PROJECT_VERSION="$build_number" \
  MARKETING_VERSION="$marketing_version" \
  clean archive

xcodebuild \
  -exportArchive \
  -archivePath "$archive_path" \
  -exportPath "$export_path" \
  -exportOptionsPlist "$export_options"

if [[ "$mode" == "--upload" ]]; then
  echo "Build ${marketing_version} (${build_number}) uploaded to App Store Connect."
else
  echo "App Store package exported to ${export_path}."
fi
